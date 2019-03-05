package solution;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.pmw.tinylog.Logger;

import problem.Problem;
import problem.Request;

public class Solution {

	static final String GROUP = "Group #";

	int index;
	
	private double cost = -1;
	
	private int nextFreeVehicleId = -1;

	Problem p;

	public List<Route> routes = new ArrayList<>();
	
	
	// should be sorted!
	public List<SolutionRequest> requests = new ArrayList<>();

	// TODO keep track of transfers

	/**
	 * @param p Associated problem instance
	 */
	public Solution(Problem p) {
		this.index = p.index;
		this.p = p;
	}
	

	/**
	 * Creates an initial solution where each request is handled by a separate
	 * vehicle/route. If the problem is infeasible, it will return a pretty close
	 * solution but log warnings.
	 */
	public void createInitialSolution() {
		Logger.debug("Trying to find a feasible solution for problem instance {000}", this.index);
		for (Request r : p.requests) {
			double dist = p.distanceBetween(r.pickupNode, r.dropoffNode);
			if (dist > r.L) {
				Logger.warn("No solution for request {000}, distance between pickup and dropoff too large", r.id);
				Logger.warn("Instance {000} is infeasible!", index);
				Logger.warn("We will try to find a close starting solution for testing purposes.");
			}		
			Route route = new Route(r.id);

			RouteNode pickup = new RouteNode(r.pickupNode, RouteNodeType.PICKUP, r, route.vehicleId);
			RouteNode dropoff = new RouteNode(r.dropoffNode, RouteNodeType.DROPOFF, r, route.vehicleId);

			// arrive early and wait\
			// preprocessing ensures this earliest time window is just reachable
			pickup.setArrival(pickup.getAssociatedNode().getE()); 
			pickup.setStartOfS(pickup.getArrival(), false);
			pickup.setNumPas(1);

			dropoff.setArrival(pickup.getDeparture() + p.distanceBetween(pickup.getAssociatedNode(), dropoff.getAssociatedNode())); // set arrival to departure + travel time
			dropoff.setStartOfS(Math.max(dropoff.getAssociatedNode().getE(), dropoff.getArrival()), false); // set start of s as early as possible
			dropoff.setNumPas(0);

			// ensure max ride time is satisfied
			if (dropoff.getStartOfS() - pickup.getDeparture() > r.L) {
				Logger.debug(
						"Instance {000}: adjusting start of service of pickup time for request {000}. Current: {0.00}",
						index, r.id, pickup.getStartOfS());
				
				double newStart = pickup.getArrival() + Math.min(dropoff.getWaiting(), pickup.getSlack()); // adjust
				pickup.setArrival(newStart);
				pickup.setStartOfS(newStart, true);
				dropoff.setArrival(pickup.getDeparture() + p.distanceBetween(dropoff.getAssociatedNode(), pickup.getAssociatedNode()));
				dropoff.setStartOfS(Math.max(dropoff.getArrival(), dropoff.getAssociatedNode().getE()), true);
			} else {
				Logger.debug(
						"Instance {000}: initial solution already okay. Pickup.SoS: {0.00} <= {0.00} <= {0.00}, dropoff.arr = {0.00}, dropoff.SoS: {0.00} <= {0.00} <= {0.00}, length = {0.00}, r.L = {0.00}",
						index, pickup.getAssociatedNode().getE(), pickup.getStartOfS(), pickup.getAssociatedNode().getL(),
						dropoff.getArrival(), dropoff.getAssociatedNode().getE(), dropoff.getStartOfS(), dropoff.getAssociatedNode().getL(),
						dropoff.getStartOfS() - pickup.getDeparture(), r.L);
			}
			
			// find starting and ending depots
			RouteNode depotStart = new RouteNode(r.pickupNode.getNearestDepot(), RouteNodeType.DEPOT_START, route.vehicleId);
			depotStart.setDeparture(pickup.getArrival() - p.distanceBetween(depotStart.getAssociatedNode(), pickup.getAssociatedNode()));

			RouteNode depotEnd = new RouteNode(r.dropoffNode.getNearestDepot(), RouteNodeType.DEPOT_END, route.vehicleId);
			depotEnd.setArrival(dropoff.getDeparture() + p.distanceBetween(depotEnd.getAssociatedNode(), dropoff.getAssociatedNode()));
			
			// keep track of the route
			route.add(depotStart);
			route.add(pickup);
			route.add(dropoff);
			route.add(depotEnd);
			this.routes.add(route);
			
			// keep track of the request
			SolutionRequest solReq = new SolutionRequest(r);
			solReq.pickup = pickup;
			solReq.dropoff = dropoff;
			pickup.setSolutionRequest(solReq);
			dropoff.setSolutionRequest(solReq);
			this.requests.add(solReq);
			
			
			this.nextFreeVehicleId = r.id + 1;
		}
		Logger.info("Found an initial solution for problem {000} with cost {0.00}", index, this.getCost());
		logSolution();
		try {
			exportSolution(true);
		} catch (FileNotFoundException e) {
			Logger.error("Cannot export file for instance {000}", this.index);
			Logger.error(e);
		}
	}
	
	public double getCost() {
		cost = 0;
		for (Route route : routes) {
			cost += route.getCost(p);
		}
		// TODO add cost of transfer locations
		return cost;
	}

	/**
	 * Writes a solution to the log. TODO: Add handling of transfers
	 */
	public void logSolution() {
		Logger.debug("Solution {000} - cost {00.00}", this.index, this.getCost());
		for (Route r : routes) {
			r.logRoute();
		}
		for (SolutionRequest sr : requests) {
			if (sr.pickup == null) {
				Logger.debug("Request {000} is currently unhandled.", sr.associatedRequest.id);
				continue;
			}
			Logger.debug("SolutionRequest {000}", sr.id);
			Logger.debug("Request {000} is picked up at node {000} at {000.00}", sr.associatedRequest.id, sr.pickup.getAssociatedNode().id, sr.pickup.getStartOfS());
			if (sr.hasTransfer()) {
				Logger.debug("Request {000} is transferred to node {000} at {000.00}", sr.associatedRequest.id, sr.transferDropoff.getAssociatedNode().id, sr.transferDropoff.getStartOfS());
				Logger.debug("Request {000} is transferred to node {000} at {000.00}", sr.associatedRequest.id, sr.transferPickup.getAssociatedNode().id, sr.transferPickup.getStartOfS());
			} 
			Logger.debug("Request {000} is dropped of at node {000} at {000.00}", sr.associatedRequest.id, sr.dropoff.getAssociatedNode().id, sr.dropoff.getStartOfS());
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
		String file = isInitial ? String.format("solutions/initial_oracs_%d.csv", index)
				: String.format("solutions/oracs_%d.csv", index);
		try (PrintWriter writer = new PrintWriter(new File(file))) {
			writer.println(GROUP);
			writer.println(index);
			writer.println(String.format("%.2f", cost));
			writer.println(routes.size());
			// print routes
			for (Route r : routes) {
				// empty line
				writer.println();
				// order of nodes
				for (int i = 0; i < r.size(); i++) {
					RouteNode rn = r.get(i);
					if (rn.getType() == RouteNodeType.TRANSFER_DROPOFF || rn.getType() == RouteNodeType.TRANSFER_PICKUP) {
						// writer.print(String.format("1%03d%03d", rn.associatedNode.id, passenger
						// person thing));
					}
					writer.print(rn.getAssociatedNode().id);
					// print commas but no trailing commas
					if (i < r.size() - 1) {
						writer.print(",");
					}
				}
				writer.println();
				// service time starts
				for (int i = 0; i < r.size(); i++) {
					RouteNode rn = r.get(i);
					if (rn.getType() == RouteNodeType.TRANSFER_DROPOFF || rn.getType() == RouteNodeType.TRANSFER_PICKUP) {
						// writer.print(String.format("1%03d%03d", rn.associatedNode.id, passenger
						// person thing));
					}
					if (rn.getType() == RouteNodeType.DEPOT_START) {
						writer.print(rn.getDeparture());
					} else if (rn.getType() == RouteNodeType.DEPOT_END) {
						writer.print(rn.getArrival());
					} else {
						writer.print(rn.getStartOfS());
					}
					// print commas but no trailing commas
					if (i < r.size() - 1) {
						writer.print(",");
					}
				}
				writer.println();
			}
		}
	}
	
	public int getNextFreeVehicleId() {
		return this.nextFreeVehicleId++;
	}
//	// TODO
//	public void calculateWaitingTimeMatrix() {
//		int numRouteNodes = 0;
//		for (Route r : routes) {
//			numRouteNodes += r.size();
//		}
//		
//		float[][] waitingTimeMatrix = new float[numRouteNodes][numRouteNodes];
//		
//	}
	
	// TODO finish this feasible method
	public boolean isFeasible() {
		return false;
	}
	
	/** 
	 * Modifies a solution by replacing a the route with the same vehicleId by the given route. 
	 * It does this by inserting new nodes and updating the timings of the old nodes, so that references from SolutionRequests to RouteNodes stay valid
	 * To ensure that the SolutionRequest that was inserted in the longer route is updated adequately, we need a reference to that too.
	 * @param newRoute the new route
	 * @param sr the SolutionRequest of the added request
	 */
	public void replaceRouteWithLongerRoute(Route newRoute, SolutionRequest sr) {
		Logger.debug("Trying to insert Route {000} into Solution {000}", newRoute.vehicleId, this.index);
		
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
			}
		}
		if (!modificationDone) {
			Logger.warn("Route {000} could not be inserted. ");
		} else {
			Logger.debug("Replaced Route {000} by modified version in solution {000}", newRoute.vehicleId, this.index);
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
		next.cost = this.cost;
		
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
				RouteNode copyRN;
				if (origRN.getType() == RouteNodeType.DEPOT_START || origRN.getType() == RouteNodeType.DEPOT_END) {
					copyRN = new RouteNode(origRN.getAssociatedNode(), origRN.getType(), origRN.getVehicleId());
				} else {
					copyRN = new RouteNode(origRN.getAssociatedNode(), origRN.getType(), origRN.getAssociatedRequest(), origRN.getVehicleId());
				}
				
				// Set all relevant fields
				// TODO ensure we update this as we update RouteNode (i.e. slack etc)
				if (origRN.getType() != RouteNodeType.DEPOT_START) { // start depots have no arrival
					copyRN.setArrival(origRN.getArrival());
				}
				if (origRN.getType() != RouteNodeType.DEPOT_END && origRN.getType() != RouteNodeType.DEPOT_START) {
					copyRN.setStartOfS(origRN.getStartOfS(), false); // depots have no service
				}
				if (origRN.getType() == RouteNodeType.DEPOT_START) { // starting depots need a manual departure time (that's the only thing they have)
					copyRN.setDeparture(origRN.getDeparture());
				}
				copyRN.setNumPas(origRN.getNumPas());
				
				// save the RouteNode in our new route
				
				copyRoute.add(copyRN);
				
				// Add RouteNode to the SolutionRequest
				int tmpRequestId = -1;
				if (origRN.getType() != RouteNodeType.DEPOT_END && origRN.getType() != RouteNodeType.DEPOT_START) {
					tmpRequestId = origRN.getAssociatedRequest().id;
				}
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
	

}
