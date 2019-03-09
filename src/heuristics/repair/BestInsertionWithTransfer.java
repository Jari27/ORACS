package heuristics.repair;

import org.pmw.tinylog.Logger;

import problem.Problem;
import problem.TransferNode;
import solution.Route;
import solution.RouteNode;
import solution.RouteNodeType;
import solution.Solution;
import solution.SolutionRequest;

public class BestInsertionWithTransfer extends RepairHeuristic {

	public BestInsertionWithTransfer(Problem p) {
		super(p);
	}
	
	private Route[] getBestNoTransferRoute(SolutionRequest toInsert, Solution sol) {
		Route[] res = {this.findBestRoute(toInsert, sol), null};
		return res;
	}
	
	
	// TODO
	private Route[] getBestTransferDropoffFirstRoute(SolutionRequest toInsert, Solution sol) {
		// we need to copy this so we can calculate the insertion cost of the second part separately
		Solution copySol = sol.copy();
		SolutionRequest copyToInsert = copySol.requests.get(copyToInsert.associatedRequest.id - 1);
		Route[] res = {null, null};
		// first we calculate the cheapest route for inserting (pi, t)
		double bestInsertionCost = 0;
		
		for (Route oldR : copySol.routes) {
			double oldCost = oldR.getCost(this.problem);
			for (int i = 1; i < oldR.size(); i++) {
				Logger.debug("Inserting pickup of request {000} in route {000} at location {000}", copyToInsert.associatedRequest.id, oldR.vehicleId, i);
				// first insert pickup
				Route onlyPickupInsert = oldR.copy(); // this also forces the cost to be recalculated
				RouteNode pickup = new RouteNode(copyToInsert.associatedRequest.pickupNode, RouteNodeType.PICKUP, copyToInsert.associatedRequest, onlyPickupInsert.vehicleId);
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
				// we now iterate over all transfers and all insertion points
				for (TransferNode t : copySol.openTransfers) {
					for (int j = i + 1; j < onlyPickupInsert.size(); j++) { // insertion at last spot and second last is same, so we dont need to check last
						Logger.debug("Inserting transfer dropoff of request {000} in route {000} at location {000}", copyToInsert.associatedRequest.id, oldR.vehicleId, j);
	
						Route insertBoth = onlyPickupInsert.copy();
						RouteNode transferDropoff = new RouteNode(t, RouteNodeType.TRANSFER_DROPOFF, copyToInsert.associatedRequest, onlyPickupInsert.vehicleId);					
	
						// do insertion at location j
						insertBoth.add(j, transferDropoff);
						// update capacity
						RouteNode prevNode = insertBoth.get(j - 1);
						transferDropoff.setNumPas(prevNode.getNumPas() - 1);
	
						if (j == onlyPickupInsert.size() - 1) { // change ending depot too
	
							RouteNode depotEnd = new RouteNode(transferDropoff.getAssociatedNode().getNearestDepot(), RouteNodeType.DEPOT_END, onlyPickupInsert.vehicleId);
							insertBoth.removeLast(); // remove depot
	
							insertBoth.addLast(depotEnd); // add nearest depot
							depotEnd.setArrival(transferDropoff.getDeparture() - problem.distanceBetween(transferDropoff.getAssociatedNode(), depotEnd.getAssociatedNode())); // set its arrival time
						}
						Logger.debug("Transfer dropoff insertion succesful at location {000}", j);
						Logger.debug("Checking feasibility of full route");
						// here we have inserted both a pickup and a transfer dropoff
						// let's verify it's feasible using and capacity
	
						if (checkTransferDropoffFeasibility(insertBoth, copyToInsert, copySol, i)) {
							double insertionCost = insertBoth.getCost(problem) - oldCost;
							if (insertionCost < bestInsertionCost || res[0] == null) {
								bestInsertionCost = insertionCost;
								res[0] = insertBoth;
								Logger.debug("Found a best route with cost = {00.00}", insertionCost);
								//bestRoute.logRoute();
							}
						}
					}
				}
			}
		}
		return res[0];
	}

	private boolean checkTransferDropoffFeasibility(Route newRoute, SolutionRequest insertedRequest, Solution solutionWithoutRoute, int locationOfFirstInsertion) {
			// verify all nodes
			for (int k = 1; k < newRoute.size(); k++) {
				RouteNode cur = newRoute.get(k);
				RouteNode prev = newRoute.get(k-1);

				// update all timings
				cur.setArrival(prev.getDeparture() + problem.distanceBetween(prev.getAssociatedNode(), cur.getAssociatedNode()));
				cur.setStartOfS(Math.max(cur.getArrival(), cur.getAssociatedNode().getE()), false); // dont report on errors, we check those manually

				// these nodes haven't changed in feasibility (they're before the insertion or a depot)
				if (k < locationOfFirstInsertion || k == newRoute.size() - 1) {
					continue;
				}

				// update SolutionRequest so it references the right route
				if (cur.getAssociatedRequest() == insertedRequest.associatedRequest) {
					switch(cur.getType()) {
					case PICKUP:
						insertedRequest.pickup = cur;
						break;
					case DROPOFF:
						insertedRequest.dropoff = cur;
						break;
					case TRANSFER_DROPOFF:
						insertedRequest.transferDropoff = cur;
						break;
					case TRANSFER_PICKUP:
						insertedRequest.transferPickup = cur;
						break;
					default:
						Logger.warn("Found a depot or default transfer that has an associated request");
						break;
					}
				}

				//Logger.debug("Checking feasibility of RouteNode {000}: {}", k, cur.toString());

				// verify time window (don't need to check start of service, since we set this to arrival or E above
				if (cur.getStartOfS() > cur.getAssociatedNode().getL()) { // infeasible
					Logger.debug("Route is infeasible due to Node {000} time window {00.00} <= {00.00} <= {00.00}", k, cur.getAssociatedNode().getE(), cur.getStartOfS(), cur.getAssociatedNode().getL());
					return false;
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
					return false;
				}
				Logger.debug("RouteNode {000} is feasible", k);

			}
			// verify max ride times of all requests
			// first check max ride time of the insert
//			if (feasible && insertedSolution.dropoff.getStartOfS() - (insertedSolution.pickup.getStartOfS() + insertedSolution.pickup.getAssociatedNode().s) > insertedSolution.associatedRequest.L) { 
//				Logger.debug("Request {000} is infeasible due max ride time {00.00} > {00.00} = L", insertedSolution.associatedRequest.id, insertedSolution.dropoff.getStartOfS() - (insertedSolution.pickup.getStartOfS() + insertedSolution.pickup.getAssociatedNode().s), insertedSolution.associatedRequest.L);
//				feasible = false;
//			} else if (feasible) {
//				Logger.debug("Request {000} is feasible due max ride time {00.00} < {00.00} = L", insertedSolution.associatedRequest.id, insertedSolution.dropoff.getStartOfS() - (insertedSolution.pickup.getStartOfS() + insertedSolution.pickup.getAssociatedNode().s), insertedSolution.associatedRequest.L);
//			}
			// then all others
			return solutionWithoutRoute.isMaxRideSatisfied();
		}
	}

	@Override
	public boolean repairSingle(SolutionRequest toInsert, Solution sol) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return null;
	}

}
