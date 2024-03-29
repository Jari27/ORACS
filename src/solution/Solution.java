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
import problem.Problem;
import problem.Request;

public class Solution {
	
	final static double ARBIT_HIGH = 100000;
	final static double ROUND_ERR = 1e-10;
	
	private int nextFreeVehicleId = -1;

	public Problem p;

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
		calcTightWindows();
		Logger.info("Found an initial solution for problem {000} with cost {0.00}", p.index, this.getCost());
		logSolution();
/*		try {
			exportSolution(true);
		} catch (FileNotFoundException e) {
			Logger.error("Cannot export file for instance {000}", p.index);
			Logger.error(e);
		}*/
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
	
	public double latestService(){
		double latestTiming = -1;
		for(SolutionRequest sr:requests){
			if(sr.dropoff.getStartOfS() > latestTiming ){
				latestTiming = sr.dropoff.getStartOfS();
			}
		}
		return latestTiming;
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
		String file = isInitial ? String.format("solutions/initial/oracs_%d.csv", p.index)
				: String.format("solutions/finished/oracs_%d.csv", p.index);
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
				writer.print(startingDepot.id + ",");
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
				writer.print(r.getFirst().getArrival() - p.distanceBetween(r.getFirst().associatedNode, startingDepot) + ",");
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
		
		// transfers
		next.openTransfers.clear();
		next.closedTransfers.clear();
		next.openTransfers.addAll(openTransfers);
		next.closedTransfers.addAll(closedTransfers);
		
		// copy routes
		for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
			Route origRoute = routes.get(routeIndex);
			Route copyRoute = origRoute.copy();
			next.routes.add(copyRoute);
			next.updateReferencesOfRoute(routeIndex);
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
		// check timewindows and capacity
		for (Route r : routes) {
			RouteNode prev = null;
			for (RouteNode rn : r) {
				if (!rn.isTransfer() && (rn.getStartOfS() - ROUND_ERR > rn.associatedNode.l || rn.getStartOfS() < rn.associatedNode.e - ROUND_ERR)) {
					Logger.debug("Not feasible because of time windows of node {000}: s = {00.00}.", rn, rn.getStartOfS());
					return false;
				} 
				if (prev == null && rn.getNumPas() != 1) {
					Logger.warn("Invalid capacity in (first) node {}", rn);
				} else if (prev != null && rn.getNumPas() - prev.getNumPas() != 1 && rn.getNumPas() - prev.getNumPas() != -1) {
					Logger.warn("Invalid capacity in nodes {} and {}. Cap1: {}. Cap2: {}", prev, rn, prev.getNumPas(), rn.getNumPas());
				}
				prev = rn;
			}
			// check timings
			prev = r.get(0);
			for (int i = 1; i < r.size(); i++) {
				RouteNode cur = r.get(i);
				if (cur.getArrival() < prev.getDeparture() + p.distanceBetween(cur.associatedNode, prev.associatedNode) - ROUND_ERR) {
					Logger.warn("Invalid arrival times of node {} and {} in route {}", cur, prev, r.vehicleId);
				}
				prev = cur;
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
			if (sr.dropoff.getStartOfS() - (sr.pickup.getStartOfS() + sr.pickup.associatedNode.s) - ROUND_ERR > sr.L) { // rounding introduces an error of 1e-15 -> sometimes says this is wrong
				// so we adjust
				Logger.debug("Not feasible because request {000} does not satisfy max ride time", sr.id);
				return false;
			}
			if (sr.hasTransfer()) {
				if (sr.transferDropoff.getStartOfS() + sr.transferDropoff.associatedNode.s - ROUND_ERR > sr.transferPickup.getStartOfS()) {
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
		//node is in multiple routes, or 
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
	
	public boolean isFeasible() {
		return calcTightWindows();
	}

	
	/*
	 * Calculates the tight time windows as described in Masson et al. 2014
	 */
	
	public boolean calcTightWindows() {
		if (npassL() && npassE()) {
			tightWindowsToSolution();
			clearReferences();
			return true;
		}
		return false;
	}
	
	private void clearReferences() {
		for (Route r : routes) {
			for (RouteNode rn : r) {
				rn.parent = null;
				rn.set = null;
				rn.scannedFrom = null;
				rn.prevInRoute = null;
			}
		}
	}


	private void tightWindowsToSolution() {
		for (Route r : routes) {
			RouteNode prev = null;
			for (RouteNode rn : r) {
				rn.setStartOfS(rn.tightE);
				if (prev == null) {
					rn.setArrival(rn.tightE);
				} else {
					rn.setArrival(prev.getDeparture() + p.distanceBetween(rn.associatedNode, prev.associatedNode));
				}
				prev = rn;
			}
		}
	}
	
//	public boolean hasNegCycle(RouteNode v, RouteNode w, double dist, List<RouteNode> L) {
//		w.tightL = dist;
//		w.parent = v;
//		w.set = L;
//		L.add(w);
//		RouteNode x = w.before;
//		w.before = null;
//		RouteNode y = w.after;
//		while (y.parent.before == null) {
//			if (y == v) {
//				return true;
//			} else {
//				y.before = null;
//				y = y.after;
//			}
//		}
//		x.after = y;
//		y.before = x;
//		w.after = v.after;
//		w.after.before = w;
//		w.before = v;
//		v.after = w;
//		return false;
//	}
//	
//	public boolean tarjanL() {
//		LinkedList<RouteNode> L = new LinkedList<>();
//		List<RouteNode> S = new ArrayList<>();
//		List<RouteNode> U = new ArrayList<>();
//		RouteNode zero = new RouteNode(null, null, 0, 0);
//		RouteNode root = new RouteNode(null, null, 0, 0);
//		zero.parent = null;
//		zero.tightL = ARBIT_HIGH;
//		zero.set = L;
//		zero.before = root;
//		zero.after = root;
//		root.before = zero;
//		root.after = zero;
//		L.add(zero);
//		
//		for (Route r : routes) {
//			RouteNode prev = null;
//			for (RouteNode rn : r) {
//				rn.parent = null;
//				rn.tightL = ARBIT_HIGH;
//				rn.set = U;
//				//U.add(rn);
//				rn.before = null;
//				rn.after = null;
//				rn.prevInRoute = prev;
//				prev = rn;
//			}
//		}
//		
//		while (L.size() > 0) {
//			RouteNode v = L.pop();
//			v.set = null;
//			if (v == zero) {
//				// edges of zero are l
//				for (Route r : routes) {
//					for (RouteNode w : r) {
//						if (!w.isTransfer()) {
//							double dist = v.tightL + w.associatedNode.l;
//							if (dist < w.tightL) {
//								if (hasNegCycle(v, w, dist, L)) {
//									return false;
//								} 
//								v.set = S;
//							}
//						}
//					}
//				}
//			}
//			// pickup has outgoing max ride time
//			if (v.type == RouteNodeType.PICKUP) {	
//				SolutionRequest sr = requests.get(v.requestId - 1);
//				RouteNode w = sr.dropoff;
//				if (w != null) {
//					double dist = v.tightL + sr.L + v.associatedNode.s;
//					if (dist < w.tightL) {	
//						if (hasNegCycle(v, w, dist, L)) {
//							return false;
//						} 
//						v.set = S;
//					}
//				}
//			}
//			// transfer pickup may hawe associated transfer dropoff
//			if (v.type == RouteNodeType.TRANSFER_PICKUP) {
//				SolutionRequest sr = requests.get(v.requestId - 1);
//				RouteNode w = sr.transferDropoff;
//				if (w != null) {
//					double dist = v.tightL - w.associatedNode.s;
//					if (dist < w.tightL) {
//						if (hasNegCycle(v, w, dist, L)) {
//							return false;
//						} 
//						v.set = S;
//					}
//				}
//			}
//			// precedence constraint
//			if (v.prevInRoute != null) {	
//				RouteNode w = v.prevInRoute;
//				double dist = v.tightL - p.distanceBetween(v.associatedNode, w.associatedNode) - w.associatedNode.s;
//				if (dist < w.tightL) {
//					if (hasNegCycle(v, w, dist, L)) {
//						return false;
//					} 
//					v.set = S;
//				}
//			}
//			// earliest starting time constraint
//			if (!v.isTransfer() && v != zero) {
//				RouteNode w = zero;
//				double dist = v.tightL - v.associatedNode.e;
//				if (dist < w.tightL) {
//					// this implies a negatiwe cycle
//					if (hasNegCycle(v, w, dist, L)) {
//						return false;
//					} 
//					v.set = S;
//					return false;
//				}
//			}
//		}
//		return true;
//	}
	
	public boolean npassL() {
		//setup
		int scansSinceLast = 0;
		LinkedList<RouteNode> A = new LinkedList<>();
		List<RouteNode> B = new ArrayList<>();
		RouteNode zero = new RouteNode(null, null, 0, 0);
		zero.parent = null;
		zero.tightL = 0;
		zero.set = B;
		B.add(zero);
		
		for (Route r : routes) {
			RouteNode prev = null;
			for (RouteNode rn : r) {
				rn.parent = null;
				rn.tightL = ARBIT_HIGH;
				rn.set = B;
				B.add(rn);
				rn.prevInRoute = prev;
				prev = rn;
			}
		}
		do {
			// asume a = empty
			// move all of B to A
			for (ListIterator<RouteNode> iter = B.listIterator(); iter.hasNext(); ) {
				RouteNode next = iter.next();
				A.add(next);
				iter.remove();
				next.set = A;
			}
			do {
				RouteNode u = A.remove();
				u.set = null;
				scansSinceLast++;
				// check all outgoing edges
				if (u == zero) {
					// edges of zero are l
					for (Route r : routes) {
						for (RouteNode v : r) {
							if (!v.isTransfer()) {
								double dist = u.tightL + v.associatedNode.l;
								if (dist < v.tightL) {
									updateNodeL(u, v, dist, B);
								}
							}
						}
					}
				}
				// pickup has outgoing max ride time
				if (u.type == RouteNodeType.PICKUP) {	
					SolutionRequest sr = requests.get(u.requestId - 1);
					RouteNode v = sr.dropoff;
					if (v != null) {
						double dist = u.tightL + sr.L + u.associatedNode.s;
						if (dist < v.tightL) {	
							updateNodeL(u, v, dist, B);
						}
					}
				}
				// transfer pickup may have associated transfer dropoff
				if (u.type == RouteNodeType.TRANSFER_PICKUP) {
					SolutionRequest sr = requests.get(u.requestId - 1);
					RouteNode v = sr.transferDropoff;
					if (v != null) {
						double dist = u.tightL - v.associatedNode.s;
						if (dist < v.tightL) {
							updateNodeL(u, v, dist, B);
						}
					}
				}
				// precedence constraint
				if (u.prevInRoute != null) {	
					RouteNode v = u.prevInRoute;
					double dist = u.tightL - p.distanceBetween(u.associatedNode, v.associatedNode) - v.associatedNode.s;
					if (dist < v.tightL) {
						updateNodeL(u, v, dist, B);
					}
				}
				// earliest starting time constraint
				if (!u.isTransfer() && u != zero) {
					RouteNode v = zero;
					double dist = u.tightL - u.associatedNode.e;
					if (dist < v.tightL) {
						// this implies a negative cycle
						updateNodeL(u, v, dist, B);
						return false;
					}
				}
			} while (A.size() > 0);
			// end of a pass, check cycle detection if long ago or last scan
			if (scansSinceLast > routes.size() * 2 || B.size() == 0) {
				if (hasNegativeCycle(zero)) {
					return false;
				} else {
					scansSinceLast = 0;
				}
			}
			
		} while (B.size() > 0);
		return true;
	}
		
	private void updateNodeL(RouteNode u, RouteNode v, double dist, List<RouteNode> B) {
		v.tightL = dist;
		v.parent = u;
		if (v.set == null) {
			B.add(v);
			v.set = B;
		}
	}
	
	public boolean npassE() {
		for (Route r1 : routes) {
			for (RouteNode s : r1) {
				int scansSinceLast = 0;
				LinkedList<RouteNode> A = new LinkedList<>();
				List<RouteNode> B = new ArrayList<>();
				
				B.add(s);
				
				RouteNode zero = new RouteNode(null, null, 0, 0);
				zero.parent = null;
				zero.negativeTightE = 0;
				zero.set = B;
				B.add(zero);
				
				for (Route r : routes) {
					RouteNode prev = null;
					for (RouteNode rn : r) {
						rn.parent = null;
						rn.negativeTightE = ARBIT_HIGH;
						rn.set = B;
						if (rn != s) {
							B.add(rn);
						}
						rn.prevInRoute = prev;
						prev = rn;
					}
				}
				s.negativeTightE = 0;
				do {
					// asume a = empty
					// move all of B to A
					for (RouteNode rn : B) {
						A.add(rn);
						rn.set = A;
					}
					B.clear();
					do {
						RouteNode u = A.remove();
						u.set = null;
						scansSinceLast++;
						// check all outgoing edges
						if (u == zero) {
							// edges of zero are l
							for (Route r : routes) {
								for (RouteNode v : r) {
									if (!v.isTransfer()) {
										double dist = u.negativeTightE + v.associatedNode.l;
										if (dist < v.negativeTightE) {
											updateNodeE(u, v, dist, B);
										}
									}
								}
							}
						}
						// pickup has outgoing max ride time
						if (u.type == RouteNodeType.PICKUP) {	
							SolutionRequest sr = requests.get(u.requestId - 1);
							RouteNode v = sr.dropoff;
							if (v != null) { // solutions can be partial, so might not be a dropoff yet
								double dist = u.negativeTightE + sr.L + u.associatedNode.s;
								if (dist < v.negativeTightE) {
									updateNodeE(u, v, dist, B);
								}
							}
						}
						// transfer pickup may have associated transfer dropoff
						if (u.type == RouteNodeType.TRANSFER_PICKUP) {
							SolutionRequest sr = requests.get(u.requestId - 1);
							RouteNode v = sr.transferDropoff;
							if (v != null) {
								double dist = u.negativeTightE - v.associatedNode.s;
								if (dist < v.negativeTightE) {
									updateNodeE(u, v, dist, B);
								}
							}
						}
						// precedence constraint
						if (u.prevInRoute != null) {	
							RouteNode v = u.prevInRoute;
							double dist = u.negativeTightE - p.distanceBetween(u.associatedNode, v.associatedNode) - v.associatedNode.s;
							if (dist < v.negativeTightE) {
								updateNodeE(u, v, dist, B);
							}
						}
						// earliest starting time constraint
						if (! u.isTransfer() && u != zero) {
							RouteNode v = zero;
							double dist = u.negativeTightE - u.associatedNode.e;
							if (dist < v.negativeTightE) {
								updateNodeE(u, v, dist, B);
							}
						}
					} while (A.size() > 0);
					// end of a pass, check cycle detection if long ago or last scan
					if (scansSinceLast > routes.size() * 2 || B.size() == 0) {
						if (hasNegativeCycle(zero)) {
							return false;
						} else {
							scansSinceLast = 0;
						}
					}
				} while (B.size() > 0);
				s.tightE = -zero.negativeTightE;
				B = null;
				A = null;
			}
		}
		return true;
	}
	
	private void updateNodeE(RouteNode u, RouteNode v, double dist, List<RouteNode> B) {
		v.negativeTightE = dist;
		v.parent = u;
		if (v.set == null) {
			B.add(v);
			v.set = B;
		}
	}

	private boolean hasNegativeCycle(RouteNode zero) {
		// setup
		for (Route r : routes) {
			for (RouteNode v : r) {
				v.scannedFrom = null;
			}
		}
		zero.scannedFrom = null;
		// check zero separately
		if (hasWalkToRootCycle(zero)) {
			return true;
		}

		for (Route r : routes) {
			for (RouteNode v : r) {
				if (hasWalkToRootCycle(v)) {
					return true;
				}
			}
		}
		return false;
	}
	
	private boolean hasWalkToRootCycle(RouteNode v) {
		RouteNode p = v;
		while (p != null) {
			if (p.scannedFrom == null) {
				p.scannedFrom = v;
				p = p.parent;
			} else if (p.scannedFrom == v && v.scannedFrom != v) {
				return true;
			} else {
				break;
			}
		}
		return false;
	}
	
	public void setRoute(int routeIndex, Route route) {
		if (routeIndex == -1) {
			routes.add(route);
			routeIndex = routes.size() - 1;
		} else {
			routes.set(routeIndex, route);
		}
		updateReferencesOfRoute(routeIndex);
	}
	
	public void addRoute(Route route) {
		setRoute(-1, route);
	}

	private void updateReferencesOfRoute(int routeIndex) {
		for (RouteNode rn : routes.get(routeIndex)) {
			SolutionRequest sr = requests.get(rn.requestId - 1);
			switch (rn.type) {
			case PICKUP:
				sr.pickup = rn;
				break;
			case DROPOFF:
				sr.dropoff = rn;
				break;
			case TRANSFER_PICKUP:
				sr.transferPickup = rn;
				break;
			case TRANSFER_DROPOFF:
				sr.transferDropoff = rn;
				break;
			}
		}
	}
	
	public void destroy() {
		for (Route r : routes){
			r.destroy();
		}
		for (SolutionRequest sr : requests) {
			sr.destroy();
		}
		openTransfers = null;
		closedTransfers = null;
		routes = null;
		requests = null;
	}
	
	//http://www.cs.princeton.edu/courses/archive/spr11/cos423/Lectures/ShortestPaths.pdf
}
