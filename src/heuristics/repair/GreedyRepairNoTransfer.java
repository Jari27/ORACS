package heuristics.repair;

import org.pmw.tinylog.Logger;

import problem.Problem;
import solution.Route;
import solution.RouteNode;
import solution.RouteNodeType;
import solution.Solution;
import solution.SolutionRequest;

public class GreedyRepairNoTransfer extends RepairHeuristic {

	public GreedyRepairNoTransfer(Problem p) {
		super(p);
	}

	@Override
	public boolean repairSingle(SolutionRequest toInsert, Solution currentSol) {
			// we do this manually now, we will use cheaper feasibility checking later
			
			// try insertion (note: creating a new route is not possible atm TODO)
			Route bestRoute = null;
			double priceOfBestRoute = 0;
			for (Route oldR : currentSol.routes) {
				for (int i = 1; i < oldR.size(); i++) {
					Logger.debug("Inserting pickup of request {000} in route {000} at location {000}", toInsert.associatedRequest.id, oldR.vehicleId, i);
					// first insert pickup
					Route onlyPickupInsert = oldR.copy(); // this also forces the cost to be recalculated
					RouteNode pickup = new RouteNode(toInsert.associatedRequest.pickupNode, RouteNodeType.PICKUP, toInsert.associatedRequest, onlyPickupInsert.vehicleId);
					//toInsert.pickup = pickup;
					
					if (i == 1) { //first place so remove/replace starting depot too
						Logger.debug("Adjusting starting depot since it might be changed due to the above insertion");
						RouteNode depotStart = new RouteNode(pickup.getAssociatedNode().getNearestDepot(), RouteNodeType.DEPOT_START, onlyPickupInsert.vehicleId);
						onlyPickupInsert.remove(0); // remove depot
						onlyPickupInsert.addFirst(pickup); // add pickup
						pickup.setArrival(pickup.getAssociatedNode().getE()); // set arrival time
						pickup.setStartOfS(pickup.getAssociatedNode().getE()); // set starting time
						pickup.setNumPas(1); // set num pass (recall its the first node)
						
						onlyPickupInsert.addFirst(depotStart); // add nearest depot
						depotStart.setDeparture(pickup.getArrival() - problem.distanceBetween(pickup.getAssociatedNode(), depotStart.getAssociatedNode())); // set its departure time
					} else { // just insert (no need for change in dropoff depot, because that depends on the dropoff location
						onlyPickupInsert.add(i, pickup);
						RouteNode prevNode = onlyPickupInsert.get(i-1);
						pickup.setArrival(prevNode.getDeparture() + problem.distanceBetween(prevNode.getAssociatedNode(), pickup.getAssociatedNode())); // set arrival
						pickup.setStartOfS(Math.max(pickup.getArrival(), pickup.getAssociatedNode().getE()), false); // max of arrival and time window (dont check errors, we do that manually)
						pickup.setNumPas(prevNode.getNumPas() + 1);
						
						// quick check if this insertion is possible
						if (pickup.getStartOfS() > pickup.getAssociatedNode().getL()) { // infeasible insertion, we cannot insert here or later so break
							Logger.debug("Insertion was infeasible due to time window {00.00} <= {00.00} <= {00.00}", pickup.getAssociatedNode().getE(), pickup.getStartOfS(), pickup.getAssociatedNode().getL());
							break;
						} else if (pickup.getNumPas() > problem.capacity) { // infeasible selection, over capacity. try next insert place
							Logger.debug("Insertion was infeasible due to capacity {} > {} = max cap", pickup.getNumPas(), problem.capacity);
							continue;						
						}
					}
					Logger.debug("Pickup insertion succesful at location {000}", i);
					// we have now inserted a new pickup (it could still be infeasible, depending on L)
					for (int j = i + 1; j < onlyPickupInsert.size(); j++) { // insertion at last spot and second last is same, so we dont need to check last
						Logger.debug("Inserting dropoff of request {000} in route {000} at location {000}", toInsert.associatedRequest.id, oldR.vehicleId, j);
						
						Route insertBoth = onlyPickupInsert.copy();
						RouteNode dropoff = new RouteNode(toInsert.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, toInsert.associatedRequest, onlyPickupInsert.vehicleId);					
						//toInsert.dropoff = dropoff;
						
						// do insertion at location j
						insertBoth.add(j, dropoff);
						// calculate timings
						RouteNode prevNode = insertBoth.get(j - 1);
						dropoff.setArrival(prevNode.getDeparture() + problem.distanceBetween(prevNode.getAssociatedNode(), dropoff.getAssociatedNode())); // set arrival time
						dropoff.setStartOfS(Math.max(dropoff.getArrival(), dropoff.getAssociatedNode().getE()), false); // set starting time as max of arrival, e (don't check errors)
						dropoff.setNumPas(prevNode.getNumPas() - 1);
						
						if (dropoff.getStartOfS() > dropoff.getAssociatedNode().getL()) {  // infeasible insertion, we cannot insert here or later so break
							Logger.debug("Insertion was infeasible due to time window {00.00} <= {00.00} <= {00.00}", dropoff.getAssociatedNode().getE(), dropoff.getStartOfS(), dropoff.getAssociatedNode().getL());
							break;
						}
						
						if (j == onlyPickupInsert.size()) { // change ending depot too
							
							RouteNode depotEnd = new RouteNode(dropoff.getAssociatedNode().getNearestDepot(), RouteNodeType.DEPOT_END, onlyPickupInsert.vehicleId);
							insertBoth.removeLast(); // remove depot
							
							insertBoth.addLast(depotEnd); // add nearest depot
							depotEnd.setArrival(dropoff.getDeparture() - problem.distanceBetween(dropoff.getAssociatedNode(), depotEnd.getAssociatedNode())); // set its arrival time
						}
						Logger.debug("Dropoff insertion succesful at location {000}", j);
						Logger.debug("Checking feasibility of full route");
						// here we have inserted both a pickup and a dropoff
						// let's verify it's feasible using ride time and time window and capacity
						boolean feasible = true;
						
						// verify all nodes
						for (int k = 1; k < insertBoth.size(); k++) {
							RouteNode cur = insertBoth.get(k);
							RouteNode prev = insertBoth.get(k-1);
							
							// update all timings
							cur.setArrival(prev.getDeparture() + problem.distanceBetween(prev.getAssociatedNode(), cur.getAssociatedNode()));
							cur.setStartOfS(Math.max(cur.getArrival(), cur.getAssociatedNode().getE()), false); // dont report on errors, we check those manually
							
							// these nodes havent changed in feasibility (they're before the insertion or a depot)
							if (k < i || k == insertBoth.size() - 1) {
								continue;
							}
							
							// update SolutionRequest so it uses the reference of the last copy
							if (cur.getAssociatedRequest() == toInsert.associatedRequest) {
								switch(cur.getType()) {
								case PICKUP:
									toInsert.pickup = cur;
									break;
								case DROPOFF:
									toInsert.dropoff = cur;
									break;
								case TRANSFER_DROPOFF:
									toInsert.transferDropoff = cur;
									break;
								case TRANSFER_PICKUP:
									toInsert.transferPickup = cur;
									break;
								default:
									Logger.warn("Found a depot or default transfer that has an associated request");
									break;
								}
							}
							
							//Logger.debug("Checking feasibility of RouteNode {000}: {}", k, cur.toString());
							
							// verify time window
							if (cur.getStartOfS() > cur.getAssociatedNode().getL()) { // infeasible
								Logger.debug("Route is infeasible due to Node {000} time window {00.00} <= {00.00} <= {00.00}", k, cur.getAssociatedNode().getE(), cur.getStartOfS(), cur.getAssociatedNode().getL());
								feasible = false;
								break;
							} 
							
							// update numpas
							int delta = 0;
							if (cur.getType() == RouteNodeType.DROPOFF || cur.getType() == RouteNodeType.TRANSFER_DROPOFF) {
								delta = -1;
							} else if (cur.getType() == RouteNodeType.PICKUP || cur.getType() == RouteNodeType.TRANSFER_PICKUP) {
								delta = 1;
							}
							cur.setNumPas(prev.getNumPas() + delta);
							
							// verify capacity
							if (cur.getNumPas() > problem.capacity) { //infeasible
								Logger.debug("Route is infeasible due to Node {000} capacity {} > {} = max cap", k, cur.getNumPas(), problem.capacity);
								feasible = false;
								break;
							}
							Logger.debug("RouteNode {000} is feasible", k);
							
						}
						// verify max ride times of all requests
						// first check max ride time of the insert
						if (feasible && toInsert.dropoff.getStartOfS() - (toInsert.pickup.getStartOfS() + toInsert.pickup.getAssociatedNode().s) > toInsert.associatedRequest.L) { 
							Logger.debug("Request {000} is infeasible due max ride time {00.00} > {00.00} = L", toInsert.associatedRequest.id, toInsert.dropoff.getStartOfS() - (toInsert.pickup.getStartOfS() + toInsert.pickup.getAssociatedNode().s), toInsert.associatedRequest.L);
							feasible = false;
						} else if (feasible) {
							Logger.debug("Request {000} is feasible due max ride time {00.00} < {00.00} = L", toInsert.associatedRequest.id, toInsert.dropoff.getStartOfS() - (toInsert.pickup.getStartOfS() + toInsert.pickup.getAssociatedNode().s), toInsert.associatedRequest.L);
						}
						// then all others
						if (feasible) {
							for (SolutionRequest sr : currentSol.requests) {
								if (sr.dropoff.getStartOfS() - (sr.pickup.getStartOfS() + sr.pickup.getAssociatedNode().s) > sr.associatedRequest.L) {
									Logger.debug("Request {000} is infeasible due max ride time {00.00} > {00.00} = L", sr.associatedRequest.id, sr.dropoff.getStartOfS() - (sr.pickup.getStartOfS() + sr.pickup.getAssociatedNode().s), sr.associatedRequest.L);
									feasible = false;
									break;
								}
							} 
						}
						
						if (feasible) {
							double tempCost = insertBoth.getCost(problem);
							if (tempCost < priceOfBestRoute || bestRoute == null) {
								priceOfBestRoute = tempCost;
								bestRoute = insertBoth;
								Logger.debug("Found a best route with cost = {00.00}", tempCost);
								bestRoute.logRoute();
							}
						}
					}
				}
			}
			// we found the cheapest route
			// if bestRoute != null ...
			//s.requests.add(toInsert);
			if (bestRoute != null) {
				Logger.debug("Best result: inserting Request {000} into Route {000}. Cost of new route: {00.00}", toInsert.associatedRequest.id, bestRoute.vehicleId, priceOfBestRoute);
				currentSol.replaceRouteWithLongerRoute(bestRoute, toInsert);
				return true;
			} else {
				Logger.debug("Could not insert Request {000} in Solution; no feasible insertions.", toInsert.associatedRequest.id);
				return false;
			}

	}

	@Override
	public String toString() {
		return "Repair Heuristic: Greedy - No Transfer";
	}

}
