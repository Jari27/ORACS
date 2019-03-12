package heuristics2.repair;

import java.util.List;

import org.pmw.tinylog.Logger;

import problem.Problem;
import solution.Route;
import solution.RouteNode;
import solution.RouteNodeType;
import solution.Solution;
import solution.SolutionRequest;

/**
 * @author Jari Meevis
 *
 */
public abstract class RepairHeuristic {
	
	Problem problem;
	
	public RepairHeuristic(Problem p) {
		this.problem = p;
	}
	
	/**
	 * Destroys a solution in place
	 * @param s the solution to destroy
	 * @param number the number of request to destroy
	 * @return true if the solution was destroyed successfully, false otherwise
	 */
	public abstract boolean repair(Solution s, List<Integer> requestIdsToRepair);
	
	protected Route findBestRoute(SolutionRequest toInsert, Solution currentSol){
		//i wanna get the pickup node corresponding to the request id
		Route bestRoute = null;
		double bestInsertionCost = 0;
		for (Route oldR : currentSol.routes){
			double oldCost = oldR.getCost(this.problem);
			for (int i = 1; i < oldR.size(); i++){
				Logger.debug("Inserting pickup of request {000} in route {000} at location {000}", toInsert, oldR.vehicleId, i);
				Route onlyPickupInsert = oldR.copy();
				RouteNode pickup = new RouteNode(toInsert.associatedRequest.pickupNode, RouteNodeType.PICKUP, toInsert.id, onlyPickupInsert.vehicleId);
				onlyPickupInsert.add(i, pickup);
				RouteNode prevNode = onlyPickupInsert.get(i-1);
				pickup.setArrival(prevNode.getDeparture() + problem.distanceBetween(prevNode.getAssociatedNode(), pickup.getAssociatedNode())); // set arrival
				pickup.setStartOfS(Math.max(pickup.getArrival(), pickup.getAssociatedNode().e), false); // max of arrival and time window (dont check errors, we do that manually)
				pickup.setNumPas(prevNode.getNumPas() + 1);
				if (pickup.getStartOfS() > pickup.getAssociatedNode().l) { // infeasible insertion, we cannot insert here or later so break
					Logger.debug("Insertion was infeasible due to time window {00.00} <= {00.00} <= {00.00}", pickup.getAssociatedNode().e, pickup.getStartOfS(), pickup.getAssociatedNode().l);
					break;
				} else if (pickup.getNumPas() > problem.capacity) { // infeasible selection, over capacity. try next insert place
					Logger.debug("Insertion was infeasible due to capacity {} > {} = max cap", pickup.getNumPas(), problem.capacity);
					continue;						
				}
				Logger.debug("Pickup insertion succesful at location {000}", i);
				// we have now inserted a new pickup (it could still be infeasible, depending on L)
				for (int j = i + 1; j < onlyPickupInsert.size(); j++) { // insertion at last spot and second last is same, so we dont need to check last
					Logger.debug("Inserting dropoff of request {000} in route {000} at location {000}", toInsert.associatedRequest.id, oldR.vehicleId, j);

					Route insertBoth = onlyPickupInsert.copy();
					RouteNode dropoff = new RouteNode(toInsert.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, toInsert.associatedRequest.id, onlyPickupInsert.vehicleId);					
					//toInsert.dropoff = dropoff;
					// do insertion at location j
					insertBoth.add(j, dropoff);
					// calculate timings
					RouteNode prevNode1 = insertBoth.get(j - 1);
					dropoff.setArrival(prevNode1.getDeparture() + problem.distanceBetween(prevNode1.getAssociatedNode(), dropoff.getAssociatedNode())); // set arrival time
					dropoff.setStartOfS(Math.max(dropoff.getArrival(), dropoff.getAssociatedNode().e), false); // set starting time as max of arrival, e (don't check errors)
					if (dropoff.getStartOfS() > dropoff.getAssociatedNode().l) {  // infeasible insertion, we cannot insert here or later so break
						Logger.debug("Insertion was infeasible due to time window {00.00} <= {00.00} <= {00.00}", dropoff.getAssociatedNode().e, dropoff.getStartOfS(), dropoff.getAssociatedNode().l);
						break;
					}

					Logger.debug("Dropoff insertion succesful at location {000}", j);
					Logger.debug("Checking feasibility of full route");
					// here we have inserted both a pickup and a dropoff
					// let's verify it's feasible using ride time and time window and capacity
					

					if (this.isFeasibleAndUpdateTimings(insertBoth, toInsert, currentSol, i)) {
						double insertionCost = insertBoth.getCost(problem) - oldCost;
						if (insertionCost < bestInsertionCost || bestRoute == null) {
							bestInsertionCost = insertionCost;
							bestRoute = insertBoth;
							Logger.debug("Found a best route with insertion cost = {00.00}. Locations: {}-{}", insertionCost, i, j);
							//bestRoute.logRoute();
						}
					}
				}
			}
		}
		return bestRoute;
	}
	protected boolean isFeasibleAndUpdateTimings(Route newRoute, SolutionRequest insertedSolution, Solution solutionWithoutRoute, int locationOfFirstInsertion){
		// verify all nodes
				for (int k = 1; k < newRoute.size(); k++) {
					RouteNode cur = newRoute.get(k);
					RouteNode prev = newRoute.get(k-1);

					// update all timings
					cur.setArrival(prev.getDeparture() + problem.distanceBetween(prev.getAssociatedNode(), cur.getAssociatedNode()));
					cur.setStartOfS(Math.max(cur.getArrival(), cur.getAssociatedNode().e), false); // dont report on errors, we check those manually

					// these nodes haven't changed in feasibility (they're before the insertion or a depot)
					if (k < locationOfFirstInsertion || k == newRoute.size() - 1) {
						continue;
					}

					// update SolutionRequest so it references the right route
					if (cur.requestId == insertedSolution.associatedRequest.id) {
						switch(cur.getType()) {
						case PICKUP:
							insertedSolution.pickup = cur;
							break;
						case DROPOFF:
							insertedSolution.dropoff = cur;
							break;
						case TRANSFER_DROPOFF:
							insertedSolution.transferDropoff = cur;
							break;
						case TRANSFER_PICKUP:
							insertedSolution.transferPickup = cur;
							break;
						default:
							Logger.warn("Found a depot or default transfer that has an associated request");
							break;
						}
					}

					//Logger.debug("Checking feasibility of RouteNode {000}: {}", k, cur.toString());

					// verify time window (don't need to check start of service, since we set this to arrival or E above
					if (cur.getStartOfS() > cur.getAssociatedNode().l) { // infeasible
						Logger.debug("Route is infeasible due to Node {000} time window {00.00} <= {00.00} <= {00.00}", k, cur.getAssociatedNode().e, cur.getStartOfS(), cur.getAssociatedNode().l);
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
					Logger.trace("RouteNode {000} is feasible", k);

				}
				// verify max ride times of all requests
				// first check max ride time of the insert
				if (insertedSolution.dropoff.getStartOfS() - (insertedSolution.pickup.getStartOfS() + insertedSolution.pickup.getAssociatedNode().s) > insertedSolution.associatedRequest.L) { 
					Logger.debug("Request {000} is infeasible due max ride time {00.00} > {00.00} = L", insertedSolution.associatedRequest.id, insertedSolution.dropoff.getStartOfS() - (insertedSolution.pickup.getStartOfS() + insertedSolution.pickup.getAssociatedNode().s), insertedSolution.associatedRequest.L);
					return false;
				} else {
					//Logger.debug("Request {000} is feasible due max ride time {00.00} < {00.00} = L", insertedSolution.associatedRequest.id, insertedSolution.dropoff.getStartOfS() - (insertedSolution.pickup.getStartOfS() + insertedSolution.pickup.getAssociatedNode().s), insertedSolution.associatedRequest.L);
				}
				// TODO we can only check max ride time after insertion
				// since we need to update the timings of the routenodes in the old solution
				Solution copy = solutionWithoutRoute.copy();
				copy.replaceRouteWithLongerRoute(newRoute, copy.requests.get(insertedSolution.associatedRequest.id - 1));
				return copy.isMaxRideSatisfied();
	}
}
