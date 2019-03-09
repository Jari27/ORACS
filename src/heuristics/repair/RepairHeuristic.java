package heuristics.repair;

import org.pmw.tinylog.Logger;

import problem.Problem;
import solution.Route;
import solution.RouteNode;
import solution.RouteNodeType;
import solution.Solution;
import solution.SolutionRequest;

public abstract class RepairHeuristic {
	
	protected Problem problem;
	
	public RepairHeuristic(Problem p) {
		this.problem = p;
	}
	
	public abstract boolean repairSingle(SolutionRequest toInsert, Solution currentSol);
	
	protected Route findBestRoute(SolutionRequest toInsert, Solution currentSol) {
		// we do this manually now, we will use cheaper feasibility checking later

		// try insertion (note: creating a new route is not possible atm TODO)
		Route bestRoute = null;
		double bestInsertionCost = 0;
		for (Route oldR : currentSol.routes) {
			double oldCost = oldR.getCost(this.problem);
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

					if (j == onlyPickupInsert.size() - 1) { // change ending depot too

						RouteNode depotEnd = new RouteNode(dropoff.getAssociatedNode().getNearestDepot(), RouteNodeType.DEPOT_END, onlyPickupInsert.vehicleId);
						insertBoth.removeLast(); // remove depot

						insertBoth.addLast(depotEnd); // add nearest depot
						depotEnd.setArrival(dropoff.getDeparture() - problem.distanceBetween(dropoff.getAssociatedNode(), depotEnd.getAssociatedNode())); // set its arrival time
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
	
	/**
	 * Checks whether a new Route is feasible. If it is, it updates the timings on the new Route to valid arrival/service times.
	 * It also updates the references of the SolutionRequest to the correct RouteNodes.
	 * 
	 * @param newRoute the new route (with inserted request)
	 * @param insertedSolution the request that was inserted
	 * @param solutionWithoutRoute the solution, currently without the new route
	 * @param locationOfFirstInsertion location of the insertion of the pickup in the new route
	 * @return
	 */
	protected boolean isFeasibleAndUpdateTimings(Route newRoute, SolutionRequest insertedSolution, Solution solutionWithoutRoute, int locationOfFirstInsertion) {
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
			if (cur.getAssociatedRequest() == insertedSolution.associatedRequest) {
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
	
	@Override
	public abstract String toString();
}
