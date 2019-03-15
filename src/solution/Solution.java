package solution;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.pmw.tinylog.Logger;

import problem.Node;
import problem.NodeType;
import problem.Problem;
import problem.Request;
import sun.misc.Queue;

public class Solution {
	
	final static double ARBIT_HIGH = 100000;

	private int nextFreeVehicleId = -1;

	Problem p;

	public List<Route> routes = new ArrayList<>();
	public List<Node> openTransfers, closedTransfers;
	
	
	// should be sorted!
	public List<SolutionRequest> requests = new ArrayList<>();

	/**
	 * @param p Associated problem instance
	 */
	public Solution(Problem p) {
		this.openTransfers = new ArrayList<>();
		this.closedTransfers = new ArrayList<>();
		this.closedTransfers.addAll(p.transfers);
		this.p = p;
	}
	

	/**
	 * Creates an initial solution where each request is handled by a separate
	 * vehicle/route. If the problem is infeasible, it will return a pretty close
	 * solution but log warnings.
	 */
	public void createInitialSolution() {
		Logger.debug("Trying to find a feasible solution for problem instance {000}", p.index);
		for (Request r : p.requests) {	
			Route route = new Route(r.id);

			RouteNode pickup = new RouteNode(r.pickupNode, RouteNodeType.PICKUP, r.id, route.vehicleId);
			RouteNode dropoff = new RouteNode(r.dropoffNode, RouteNodeType.DROPOFF,  r.id, route.vehicleId);

			// arrive early and wait\
			// preprocessing ensures this earliest time window is just reachable
			pickup.setArrival(pickup.associatedNode.e); 
			pickup.setStartOfS(pickup.getArrival(), false);
			pickup.setNumPas(1);

			dropoff.setArrival(pickup.getDeparture() + p.distanceBetween(pickup.associatedNode, dropoff.associatedNode)); // set arrival to departure + travel time
			dropoff.setStartOfS(Math.max(dropoff.associatedNode.e, dropoff.getArrival()), false); // set start of s as early as possible
			dropoff.setNumPas(0);

			// ensure max ride time is satisfied
			if (dropoff.getStartOfS() - pickup.getDeparture() > r.L) {
				Logger.trace(
						"Instance {000}: adjusting start of service of pickup time for request {000}. Current: {0.00}",
						p.index, r.id, pickup.getStartOfS());
				double newStart = pickup.getArrival() + (dropoff.getStartOfS() - dropoff.getArrival());
				pickup.setArrival(newStart);
				pickup.setStartOfS(newStart, true);
				dropoff.setArrival(pickup.getDeparture() + p.distanceBetween(dropoff.associatedNode, pickup.associatedNode));
				dropoff.setStartOfS(Math.max(dropoff.getArrival(), dropoff.associatedNode.e), true);
			} else {
				Logger.trace(
						"Instance {000}: initial solution already okay. Pickup.SoS: {0.00} <= {0.00} <= {0.00}, dropoff.arr = {0.00}, dropoff.SoS: {0.00} <= {0.00} <= {0.00}, length = {0.00}, r.L = {0.00}",
						p.index, pickup.associatedNode.e, pickup.getStartOfS(), pickup.associatedNode.l,
						dropoff.getArrival(), dropoff.associatedNode.e, dropoff.getStartOfS(), dropoff.associatedNode.l,
						dropoff.getStartOfS() - pickup.getDeparture(), r.L);
			}
			
			// keep track of the route
			route.add(pickup);
			route.add(dropoff);
			this.routes.add(route);
			
			// keep track of the request
			SolutionRequest solReq = new SolutionRequest(r);
			solReq.pickup = pickup;
			solReq.dropoff = dropoff;
			
			this.requests.add(solReq);
			this.nextFreeVehicleId = r.id + 1;
		}
		Logger.info("Found an initial solution for problem {000} with cost {0.00}", p.index, this.getCost());
		logSolution();
		try {
			exportSolution(true);
		} catch (FileNotFoundException e) {
			Logger.error("Cannot export file for instance {000}", p.index);
			Logger.error(e);
		}
	}
	
	public double getCost() {
		double cost = 0;
		for (Route route : routes) {
			cost += route.getCost(p);
		}
		for (Node transfer : openTransfers) {
			cost += transfer.f;
		}
		return cost;
	}

	/**
	 * Writes a solution to the log.
	 */
	public void logSolution() {
		Logger.debug("Cost of the current solution: {00.00}", this.getCost());
		for (Route r : routes) {
			r.logRoute();
		}
		for (SolutionRequest sr : requests) {
			if (sr.pickup == null) {
				Logger.debug("Request {000} is currently unhandled.", sr.associatedRequest.id);
				continue;
			}
			Logger.debug("SolutionRequest {000}", sr.id);
			Logger.debug("Request {000} is picked up at node {000} at {000.00}", sr.associatedRequest.id, sr.pickup.associatedNode.id, sr.pickup.getStartOfS());
			if (sr.hasTransfer()) {
				Logger.debug("Request {000} is transferred to node {000} at {000.00}", sr.associatedRequest.id, sr.transferDropoff.associatedNode.id, sr.transferDropoff.getStartOfS());
				Logger.debug("Request {000} is transferred to node {000} at {000.00}", sr.associatedRequest.id, sr.transferPickup.associatedNode.id, sr.transferPickup.getStartOfS());
			} 
			Logger.debug("Request {000} is dropped of at node {000} at {000.00}", sr.associatedRequest.id, sr.dropoff.associatedNode.id, sr.dropoff.getStartOfS());
		}
	}

	/**
	 * Exports a solution as CSV to the default location
	 * (/solutions/[initial_]oracs_{id}.csv).
	 * 
	 * @throws FileNotFoundException
	 */
	public void exportSolution(boolean isInitial) throws FileNotFoundException {
		// TODO handle transfers
		String file = isInitial ? String.format("solutions/initial_oracs_%d.csv", p.index)
				: String.format("solutions/oracs_%d.csv", p.index);
		try (PrintWriter writer = new PrintWriter(new File(file))) {
			writer.println("Group 6");
			writer.println(p.index);
			writer.println(String.format("%.2f", this.getCost()));
			writer.println(routes.size());
			// print routes
			for (Route r : routes) {
				// empty line
				writer.println();
				// order of nodes
				// starting depot
				Node startingDepot = p.getNearestDepot(r.getFirst().associatedNode);
				writer.print(startingDepot.id);
				for (int i = 0; i < r.size(); i++) {
					RouteNode rn = r.get(i);
					if (rn.getType() == RouteNodeType.TRANSFER_DROPOFF) {
						writer.print(String.format("10%03d%03d", rn.associatedNode.id, rn.requestId));
					} else if (rn.getType() == RouteNodeType.TRANSFER_PICKUP) {
						writer.print(String.format("11%03d%03d", rn.associatedNode.id, rn.requestId));
					} else {
						writer.print(rn.associatedNode.id);
					}
					writer.print(",");
				}
				// ending depot
				Node endingDepot = p.getNearestDepot(r.getLast().associatedNode);
				writer.print(endingDepot.id);
				
				writer.println();
				// service time starts
				// starting depot departure time
				writer.print(r.getFirst().getArrival() - p.distanceBetween(r.getFirst().associatedNode, startingDepot));
				for (int i = 0; i < r.size(); i++) {
					RouteNode rn = r.get(i);
					writer.print(rn.getStartOfS());
					writer.print(",");
				}
				// ending depot arrival time
				writer.print(r.getLast().getDeparture() + p.distanceBetween(r.getLast().associatedNode, endingDepot));
				writer.println();
			}
		}
	}
	
	public int getNextFreeVehicleId() {
		return this.nextFreeVehicleId++;
	}
	
	
	/** 
	 * Modifies a solution by replacing a the route with the same vehicleId by the given route. 
	 * It does this by inserting new nodes and updating the timings of the old nodes, so that references from SolutionRequests to RouteNodes stay valid
	 * To ensure that the SolutionRequest that was inserted in the longer route is updated adequately, we need a reference to that too.
	 * @param newRoute the new route
	 * @param sr the SolutionRequest of the added request
	 */
	public void replaceRouteWithLongerRoute(Route newRoute, SolutionRequest sr) {
		Logger.debug("Trying to insert Route {000}", newRoute.vehicleId);
		
		// try to replace a route
		boolean modificationDone = false; // check if we need to insert
		for (Route oldRoute : this.routes) {
			if (oldRoute.vehicleId == newRoute.vehicleId) {
				
				// preprocess starting depot
				// since this is not stored anywhere else, we do not need to update the references
				// we can instead directly replace the RouteNode
				oldRoute.removeFirst();
				oldRoute.addFirst(newRoute.getFirst());
				
				for (int i = 1; i < newRoute.size() - 1; i++) { // check the biggest one because we insert in the smallest one
					// we check if each node is that same by comparing associated nodes
					// for non-transfers, that is enough since each pickup/dropoff is visited only once
					// so if the associated nodes are the same, the RouteNodes are the same object (albeit with a different reference due to copying)
					// for transfers we also check the type and associated request
					RouteNode toUpdate = oldRoute.get(i);
					RouteNode newTimings = newRoute.get(i);
					if (toUpdate.isEqualExceptTimings(newTimings)) {
						Logger.debug("RouteNode ({}) and RouteNode ({}) are the same.", toUpdate, newTimings);
						if (modificationDone) {
							// we have inserted at least one node, so we need to change subsequent timings
							toUpdate.setArrival(newTimings.getArrival());
							toUpdate.setStartOfS(newTimings.getStartOfS());
							toUpdate.setNumPas(newTimings.getNumPas());
						}
					} else {
						// different node, so insert
						modificationDone = true;
						oldRoute.add(i, newTimings);
						switch (newTimings.getType()) {
						case PICKUP:
							sr.pickup = newTimings;
							break;
						case DROPOFF:
							sr.dropoff = newTimings;
							break;
						case TRANSFER_PICKUP:
							sr.transferPickup = newTimings;
							break;
						case TRANSFER_DROPOFF:
							sr.transferDropoff = newTimings;
							break;
						default:
							Logger.warn("Problem while inserting RouteNode ({}) into Route {000} at location {000}: it has no valid type", newTimings, oldRoute.vehicleId, i);
							break;
						}
					}
				}
				// replace last depot
				oldRoute.removeLast();
				oldRoute.addLast(newRoute.getLast());
				break;
			}

		}
		if (!modificationDone) {
			Logger.warn("Route {000} could not be inserted. ");
		} else {
			Logger.debug("Replaced Route {000} by modified version", newRoute.vehicleId);
		}
	}
	
	/**
	 * Makes a deep copy of the current solution. Each object (except the problem instance) is copied in turn.
	 * TODO: Update this as the objects changes. We expect to change the slack in the RouteNode and to add a way of keeping track of the transfers
	 * 
	 * @return a copy of this solution object
	 */
	public Solution copy() {
		Solution next = new Solution(this.p);
		next.nextFreeVehicleId = this.nextFreeVehicleId;
		
		// Create solution requests
		// we make this early so we can assign the correct RouteNodes to them as soon as we make those
		// see below
		for (Request r : p.requests) {
			SolutionRequest solReq = new SolutionRequest(r);
			next.requests.add(solReq);
		}
		
		// copy routes
		for (Route origRoute : routes) {
			Route copyRoute = new Route(origRoute.vehicleId); // forces recalculation of costs
			copyRoute.setRouteUnchanged(); // prevents cost recalculation
			copyRoute.setCost(origRoute.getCost(p)); // sets cost correctly
			
			for (RouteNode origRN : origRoute) {
				// create a new RouteNode and set its associated node, type and request (if not a depot)
				// note; the associated node does not have to be copied since it is the same
				RouteNode copyRN = new RouteNode(origRN.associatedNode, origRN.getType(), origRN.requestId, origRN.vehicleId);
				
				// Set all relevant fields
				// TODO ensure we update this as we update RouteNode (i.e. slack etc)
				copyRN.setArrival(origRN.getArrival());
				copyRN.setStartOfS(origRN.getStartOfS(), false); 
				copyRN.setNumPas(origRN.getNumPas());
				
				// save the RouteNode in our new route
				copyRoute.add(copyRN);
				
				// Add RouteNode to the SolutionRequest
				int tmpRequestId = origRN.requestId;
				switch (origRN.getType()) {
				case PICKUP:
					next.requests.get(tmpRequestId - 1).pickup = copyRN; // our requests are 1-indexed instead of 0
					break;
				case DROPOFF:
					next.requests.get(tmpRequestId - 1).dropoff = copyRN;
					break;
				case TRANSFER_PICKUP:
					next.requests.get(tmpRequestId - 1).transferPickup = copyRN;
					break;
				case TRANSFER_DROPOFF:
					next.requests.get(tmpRequestId - 1).transferDropoff = copyRN;
					break;
				default:
					break;
				}
			}
			// save the route in our new solution
			next.routes.add(copyRoute);
		}
		return next;
	}
	
	public boolean isMaxRideSatisfied() {
		for (SolutionRequest sr : this.requests) {
			if (sr.pickup == null || sr.dropoff == null) {
				Logger.warn("Request {000} is unplanned (or not correctly updated)!", sr.id);
				return false;
			}
			if (sr.dropoff.getStartOfS() - (sr.pickup.getStartOfS() + sr.pickup.associatedNode.s) > sr.associatedRequest.L) {
				Logger.debug("Max ride time of request {000} not satisfied. {00.00} - ({00.00} + {00.00}) > {}", sr.id, sr.dropoff.getStartOfS(), sr.pickup.getStartOfS(), sr.pickup.associatedNode.s, sr.associatedRequest.L);
				return false;
			}
		}
		return true;
	}
	
	public boolean isFeasibleVerify(boolean canBePartial) {
		// check timewindows
		for (Route r : routes) {
			for (RouteNode rn : r) {
				if (!rn.isTransfer() && (rn.getStartOfS() > rn.associatedNode.l || rn.getStartOfS() < rn.associatedNode.e)) {
					Logger.debug("Not feasible because of time windows of node {000}: s = {00.00}.", rn, rn.getStartOfS());
					return false;
				}
			}
			// check timings
			for (int i = 1; i < r.size(); i++) {
				RouteNode prev = r.get(i-1);
				RouteNode cur = r.get(i);
				if (cur.getArrival() < prev.getDeparture() + p.distanceBetween(cur.associatedNode, prev.associatedNode)) {
					Logger.warn("Invalid arrival times of node {} and {} in route {}", cur, prev, r.vehicleId);
				}
			}
		}
		// check max ride time && transfer precedence
		for (SolutionRequest sr : requests) {
			if ((sr.pickup == null || sr.dropoff == null || (sr.transferDropoff == null && sr.transferPickup != null) || (sr.transferDropoff != null && sr.transferPickup == null))) {
				if (!canBePartial) {
					Logger.warn("Unplanned request! {000}", sr.id);
				}
				continue;
			}
			if (sr.dropoff.getStartOfS() - (sr.pickup.getStartOfS() + sr.pickup.associatedNode.s) > sr.L) {
				Logger.debug("Not feasible because request {000} does not satisfy max ride time", sr.id);
				return false;
			}
			if (sr.hasTransfer()) {
				if (sr.transferDropoff.getStartOfS() + sr.transferDropoff.associatedNode.s < sr.transferPickup.getStartOfS()) {
					Logger.warn("Pickup before dropoff! Impossible. Request: {000}", sr.id);
				}
				if (sr.transferDropoff.associatedNode != sr.transferPickup.associatedNode) {
					Logger.warn("Transfer pickup and dropoff at different nodes (wtf?)");
				}
			}
		}
		return true;
	}
	
	public boolean hasOrphanRouteNodes() {
		boolean isCorrect = true;
		Set<RouteNode> routeNodesFromRoutes = new HashSet<>();
		Set<RouteNode> routeNodesFromRequests = new HashSet<>();
		for (Route r : routes) {
			for (RouteNode rn : r) {
				if (routeNodesFromRoutes.contains(rn)) {
					Logger.warn("Same routenode in multiple routes. Node = {}, second Route = {000}", rn.toString(), r.vehicleId);
					isCorrect = false;
				} else {
					routeNodesFromRoutes.add(rn);
				}
			}
		}
		for (SolutionRequest sr : requests) {
			routeNodesFromRequests.add(sr.pickup);
			routeNodesFromRequests.add(sr.dropoff);
			if (sr.hasTransfer()) {
				routeNodesFromRequests.add(sr.transferPickup);
				routeNodesFromRequests.add(sr.transferDropoff);
			}
		}
		for (RouteNode r : routeNodesFromRequests) {
			if (!routeNodesFromRoutes.contains(r)) {
				Logger.warn("RouteNode {} from requests not in any route", r);
				isCorrect = false;
			}
		}
		for (RouteNode r : routeNodesFromRoutes) {
			if (!routeNodesFromRequests.contains(r)) {
				Logger.warn("RouteNode {} from route not in any request", r);
				isCorrect = false;
			}
		}
		return !isCorrect;
	}
	
	// TODO NC1, NC2
	public boolean isFeasible() {
		if (calcTightWindows()) {
			return true;
		}
		return false;
	}

	
	/*
	 * Calculates the tight time windows as described in Masson et al. 2014
	 */
	
	// TODO how in V2?
	public boolean calcTightWindows() {
		calcTightL();
		if (calcTightL() && calcTightE()) {
			tightWindowsToSolution();
			return true;
		}
		return false;
	}
	
	private void tightWindowsToSolution() {
		// TODO Auto-generated method stub
		
	}
	
	//http://www.cs.princeton.edu/courses/archive/spr11/cos423/Lectures/ShortestPaths.pdf
	public boolean calcTightL() {
		LinkedList<RouteNode> labeled = new LinkedList<>();
		LinkedList<RouteNode> scanned = new LinkedList<>();
		LinkedList<RouteNode> unreached = new LinkedList<>();
		
		for (Route r : routes) {
			for (RouteNode rn : r) {
				rn.tightL = ARBIT_HIGH;
				rn.parent = null;
				rn.before = null;
				unreached.add(rn);
			}
		}
		RouteNode zero = new RouteNode(null, null, 0, 0);
		zero.parent = null;
		zero.tightL = 0;
		zero.before = zero;
		zero.after = zero;
		
		labeled.add(zero);
		
		// at this moment, zero is labeled, all others are unscanned
		// first we calculate all outgoing arcs of fakezero
		RouteNode v = labeled.remove();
		for (Route r : routes) {
			for (RouteNode w : r) {
				if (!w.isTransfer()) { // only non-transfer have time windows = arcs
					double delta = w.tightL - v.tightL - w.associatedNode.l;
					if (delta > 0) {
						w.tightL = w.tightL - delta;
						w.parent = v;
						labeled.add(w);
						if (hasNegativeCycleL(v, w, delta)) {
							return false;
						} else {
							scanned.add(v);
						}
					}
				}
			}
		}
		// zero = scanned
		// all others except transfers are labeled
		// transfers are unreached
		for (Route r : routes) {
			RouteNode prev = null;
			for (RouteNode v1 : r) {
				// check all outgoing arcs of v
				// max ride constraint
				if (v1.type == RouteNodeType.PICKUP) {
					SolutionRequest sr = requests.get(v1.requestId - 1);
					RouteNode w = sr.dropoff;
					double delta = w.tightL - v1.tightL - sr.L;
					if (delta > 0) {
						w.tightL = w.tightL - delta;
						w.parent = v1; 
						labeled.add(w);
						if (hasNegativeCycleL(v1, w, delta)) {
							return false;
						} 
					}
				}
				// transfer precedence constraint
				if (v1.type == RouteNodeType.TRANSFER_PICKUP) {
					SolutionRequest sr = requests.get(v1.requestId - 1);
					RouteNode w = sr.transferDropoff;
					double delta = w.tightL - v1.tightL - (-w.associatedNode.s);
					if (delta > 0) {
						w.tightL = w.tightL - delta;
						w.parent = v1;
						labeled.add(w);
						if (hasNegativeCycleL(v1, w, delta)) {
							return false;
						}
					}
				}
				// route precedence constraint
				if (prev != null) {
					RouteNode w = prev;
					double delta = w.tightL - v1.tightL - (-p.distanceBetween(w.associatedNode, v.associatedNode) - prev.associatedNode.s);
					if (delta > 0) {
						w.tightL = w.tightL - delta;
						w.parent = v1;
						labeled.add(w);
						if (hasNegativeCycleL(v1, w, delta)) {
							return false;
						}
					}
				}
				scanned.add(v);
			}
		}
		return true;
	}


	private boolean hasNegativeCycleL(RouteNode v, RouteNode w, double delta) {
		if (v == v.before) {
			v.after = w;
			v.before = w;
			w.after = v;
			w.before = v;
			return false; // if we only have one node it cannot have a negative cycle yet
		}
		RouteNode x = w.before;
		w.before = null;
		RouteNode y = w.after;
		while (x != null && y.parent.before == null) {
			if (y == v) {
				return true;
			} else {
				y.tightL = y.tightL - delta + 0.00001;
				y.before = null;
				y = y.after;
			}
		}
		if (x != null) {
			x.after = y;
			y.before = x;
		}
		w.after = v.after;
		w.after.before = w;
		w.before = v;
		v.after = w;
		return false;
	}


	// Tarjans algorithm with distance updates
//	public boolean calcTightL() {
//		RouteNode fakeZero = new RouteNode(null, null, 0, 0);
//		fakeZero.tightL = 0;
//		fakeZero.before = fakeZero;
//		fakeZero.after = fakeZero;
//		
//		for (Route r : routes) {
//			for (RouteNode rn : r) {
//				rn.tightL = ARBIT_HIGH;//rn.associatedNode.l;
//				rn.before = null;
//				rn.parent = null;
//				//rn.after = null;
//			}
//		}
//		// calculate fakezero first since it has different arcs
//		for (Route r : routes) {
//			for (RouteNode w : r) { // these are all arcs out of fakeZero
//				double delta = w.tightL - fakeZero.tightL - Math.min(w.associatedNode.l, ARBIT_HIGH); // prevent numbers so high the calcs are shit
//				if (w.associatedNode.type == NodeType.TRANSFER) {
//					// transfer have to time windows, so delta = inf - 0 - inf = 0
//					delta = 0;
//				}
//				if (delta > 0) {
//					if (!checkNegCycleL(fakeZero, w, delta)) {
//						return false;
//					}
//				}
//			}
//		}
//		
//		// all routenodes are now part of labeled
//		// so we can iterate over the routes instead of labeled
//		for (Route route : routes) {
//			int s = route.size();
//			RouteNode prev = null;
//			for (int i = 0; i < s; i++) {
//				RouteNode v = route.get(i);
//				// for each outgoing arc, do the inner loop
//				// max ride time arc
//				if (v.type == RouteNodeType.PICKUP) {
//					// get associated node and update it
//					SolutionRequest sr = requests.get(v.requestId - 1);
//					double delta = sr.dropoff.tightL - v.tightL - sr.L; // length of arc is L
//					if (delta > 0) {
//						if (!checkNegCycleL(v, sr.dropoff, delta)) {
//							return false;
//						}
//					}
//				}
//				// previous node arc
//				if (prev != null) {
//					double delta = prev.tightL - v.tightL - (-p.distanceBetween(v.associatedNode, prev.associatedNode) - prev.associatedNode.s);
//					if (delta > 0) {
//						if (!checkNegCycleL(v, prev, delta)) {
//							return false;
//						}
//					}
//				} 
//				// transfer precedence arc
//				if (v.type == RouteNodeType.TRANSFER_PICKUP) {
//					SolutionRequest sr = requests.get(v.requestId - 1);
//					double delta = sr.transferDropoff.tightL - v.tightL - (-sr.transferDropoff.associatedNode.s);
//					if (delta > 0) {
//						if (!checkNegCycleL(v, prev, delta)) {
//							return false;
//						}
//					}
//				}
//				prev = v;
//			}	
//		}
//		return true;
//	}
	
	// source: http://www.cs.princeton.edu/courses/archive/spr11/cos423/Lectures/ShortestPaths.pdf, slide 44
//	private boolean checkNegCycleL(RouteNode v, RouteNode w, double delta) {
//		w.tightL -= delta;
//		w.parent = v;
//		RouteNode x = w.before;
//		w.before = null;
//		RouteNode y = w.after;
//		while (y != null && y.parent != null && y.parent.before == null) { // modified
//			if (y == v && y.after != y) {
//				// found a negative cycle
//				return false;
//			} else {
//				y.tightL = y.tightL - delta + 0.00001;
//				y.before = null;
//				y = y.after;
//			}
//		}
//		if (x != null) { //modifed
//			x.after = y;
//		}
//		if (y != null) { // modified
//			y.before = x;
//		}
//		w.after = v.after;
//		w.after.before = w;
//		w.before = v;
//		v.after = w;
//		return true;
//	}
	
	public boolean calcTightE() {
		RouteNode fakeZero = new RouteNode(null, null, 0, 0);
		fakeZero.negativeTightE = 0;
		fakeZero.before = fakeZero;
		fakeZero.after = fakeZero;
		
		
		// for easy arc finding
		for (Route r : routes) {
			RouteNode prev = null;
			for (RouteNode rn : r) {
				rn.prev = prev;
				prev = rn;
			}
		}
		
		for (Route route : routes) {
			for (RouteNode s : route) {
				LinkedList<RouteNode> labeled = new LinkedList<>();
				LinkedList<RouteNode> scanned = new LinkedList<>();
				LinkedList<RouteNode> unreached = new LinkedList<>();
				fakeZero.negativeTightE = 0;
				fakeZero.before = null;
				fakeZero.after = null;
				unreached.add(fakeZero);	
				
				for (Route r : routes) {
					for (RouteNode rn : r) {
						rn.negativeTightE = ARBIT_HIGH;
						rn.before = null;
						rn.parent = null;
						unreached.add(rn);
					}
				}
				unreached.remove(s);
				labeled.add(s);
				s.after = s;
				s.before = s;
				s.negativeTightE = 0;
				
				while (labeled.size() > 0) {
					RouteNode v = labeled.remove();
					// for each arc out of v
					// if v is fakezero it has tons of arcs
					if (v == fakeZero) {
						
					}
				}
				
		}
		return true;
	}


	private boolean checkNegCycleE(RouteNode v, RouteNode w, double delta) {
		RouteNode x = w.before;
		w.before = null;
		RouteNode y = w.after;
		while (y != null && y.parent != null && y.parent.before == null) { // modified
			if (y == v && y.after != y) {
				// found a negative cycle
				return false;
			} else {
				y.tightE = y.tightE - delta + 0.00001;
				y.before = null;
				y = y.after;
			}
		}
		if (x != null) { //modifed
			x.after = y;
		}
		if (y != null) { // modified
			y.before = x;
		}
		w.after = v.after;
		w.after.before = w;
		w.before = v;
		v.after = w;
		return true;
	}

}
