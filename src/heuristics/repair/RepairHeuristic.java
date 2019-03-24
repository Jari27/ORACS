package heuristics.repair;

import java.util.List;

import org.pmw.tinylog.Logger;

import problem.Problem;
import problem.Request;
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

	public RepairHeuristic(Problem problem) {
		this.problem = problem;
	}

	/**
	 * Destroys a solution in place
	 * 
	 * @param s      the solution to destroy
	 * @param number the number of request to destroy
	 * @return true if the solution was destroyed successfully, false otherwise
	 */
	public abstract boolean repair(Solution s, List<Integer> requestIdsToRepair);

	protected Route findBestRoute(SolutionRequest toInsert, Solution currentSol) {
		Route bestRoute = null;
		double bestInsertionCost = 0;
		for (Route oldR : currentSol.routes) {
			double oldCost = oldR.getCost(this.problem);
			for (int i = 1; i < oldR.size(); i++) {
				Logger.debug("Inserting pickup of request {000} in route {000} at location {000}",
						toInsert.associatedRequest.id, oldR.vehicleId, i);
				Route onlyPickupInsert = oldR.copy();
				RouteNode pickup = new RouteNode(toInsert.associatedRequest.pickupNode, RouteNodeType.PICKUP,
						toInsert.id, onlyPickupInsert.vehicleId);
				onlyPickupInsert.add(i, pickup);
				RouteNode prevNode = onlyPickupInsert.get(i - 1);
				pickup.setArrival(prevNode.getDeparture()
						+ problem.distanceBetween(prevNode.associatedNode, pickup.associatedNode)); // set arrival
				pickup.setStartOfS(Math.max(pickup.getArrival(), pickup.associatedNode.e), false); // max of arrival and
																									// time window (dont
																									// check errors, we
																									// do that manually)
				pickup.setNumPas(prevNode.getNumPas() + 1);
				if (pickup.getStartOfS() > pickup.associatedNode.l) { // infeasible insertion, we cannot insert here or
																		// later so break
					Logger.debug("Insertion was infeasible due to time window {00.00} <= {00.00} <= {00.00}",
							pickup.associatedNode.e, pickup.getStartOfS(), pickup.associatedNode.l);
					break;
				} else if (pickup.getNumPas() > problem.capacity) { // infeasible selection, over capacity. try next
																	// insert place
					Logger.debug("Insertion was infeasible due to capacity {} > {} = max cap", pickup.getNumPas(),
							problem.capacity);
					continue;
				}
				Logger.debug("Pickup insertion succesful at location {000}", i);
				// we have now inserted a new pickup (it could still be infeasible, depending on
				// L)
				for (int j = i + 1; j < onlyPickupInsert.size(); j++) { // insertion at last spot and second last is
																		// same, so we dont need to check last
					Logger.debug("Inserting dropoff of request {000} in route {000} at location {000}",
							toInsert.associatedRequest.id, oldR.vehicleId, j);

					Route insertBoth = onlyPickupInsert.copy();
					RouteNode dropoff = new RouteNode(toInsert.associatedRequest.dropoffNode, RouteNodeType.DROPOFF,
							toInsert.associatedRequest.id, onlyPickupInsert.vehicleId);
					// toInsert.dropoff = dropoff;
					// do insertion at location j
					insertBoth.add(j, dropoff);
					// calculate timings
					RouteNode prevNode1 = insertBoth.get(j - 1);
					dropoff.setArrival(prevNode1.getDeparture()
							+ problem.distanceBetween(prevNode1.associatedNode, dropoff.associatedNode)); // set arrival
																											// time
					dropoff.setStartOfS(Math.max(dropoff.getArrival(), dropoff.associatedNode.e), false); // set
																											// starting
																											// time as
																											// max of
																											// arrival,
																											// e (don't
																											// check
																											// errors)
					if (dropoff.getStartOfS() > dropoff.associatedNode.l) { // infeasible insertion, we cannot insert
																			// here or later so break
						Logger.debug("Insertion was infeasible due to time window {00.00} <= {00.00} <= {00.00}",
								dropoff.associatedNode.e, dropoff.getStartOfS(), dropoff.associatedNode.l);
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
							Logger.debug("Found a best route with insertion cost = {00.00}. Locations: {}-{}",
									insertionCost, i, j);
							// bestRoute.logRoute();
						}
					}
				}
			}
		}
		// In case the insertion is not possible, we have to deploy a new vehicle
		if (bestRoute == null) {
			Logger.debug("Insertion was infeasible, creating new route...");
			int newVehicleID = currentSol.getNextFreeVehicleId();
			Route newRoute = new Route(newVehicleID);
			RouteNode pickup = toInsert.pickup;
			RouteNode dropoff = toInsert.dropoff;
			pickup.setArrival(pickup.associatedNode.e);
			pickup.setStartOfS(pickup.getArrival(), false);
			pickup.setNumPas(1);

			dropoff.setArrival(
					pickup.getDeparture() + problem.distanceBetween(pickup.associatedNode, dropoff.associatedNode)); // set
																														// arrival
																														// to
																														// departure
																														// +
																														// travel
																														// time
			dropoff.setStartOfS(Math.max(dropoff.associatedNode.e, dropoff.getArrival()), false); // set start of s as
																									// early as possible
			dropoff.setNumPas(0);

			if (dropoff.getStartOfS() - pickup.getDeparture() > toInsert.associatedRequest.L) {
				Logger.trace(
						"Instance {000}: adjusting start of service of pickup time for request {000}. Current: {0.00}",
						problem.index, toInsert.id, pickup.getStartOfS());
				double newStart = pickup.getArrival() + (dropoff.getStartOfS() - dropoff.getArrival());
				pickup.setArrival(newStart);
				pickup.setStartOfS(newStart, true);
				dropoff.setArrival(
						pickup.getDeparture() + problem.distanceBetween(dropoff.associatedNode, pickup.associatedNode));
				dropoff.setStartOfS(Math.max(dropoff.getArrival(), dropoff.associatedNode.e), true);
			}
			newRoute.add(pickup);
			newRoute.add(dropoff);
			currentSol.routes.add(newRoute);
			return newRoute;
		} else {
			return bestRoute;
		}
	}

	protected boolean isFeasibleAndUpdateTimings(Route newRoute, SolutionRequest insertedSolution,
			Solution solutionWithoutRoute, int locationOfFirstInsertion) {
		// verify all nodes
		for (int k = 1; k < newRoute.size(); k++) {
			RouteNode cur = newRoute.get(k);
			RouteNode prev = newRoute.get(k - 1);

			// update all timings
			cur.setArrival(prev.getDeparture() + problem.distanceBetween(prev.associatedNode, cur.associatedNode));
			cur.setStartOfS(Math.max(cur.getArrival(), cur.associatedNode.e), false); // dont report on errors, we check
																						// those manually

			// these nodes haven't changed in feasibility (they're before the insertion or a
			// depot)
			if (k < locationOfFirstInsertion || k == newRoute.size() - 1) {
				continue;
			}

			// update SolutionRequest so it references the right route
			if (cur.requestId == insertedSolution.associatedRequest.id) {
				switch (cur.getType()) {
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

			// Logger.debug("Checking feasibility of RouteNode {000}: {}", k,
			// cur.toString());

			// verify time window (don't need to check start of service, since we set this
			// to arrival or E above
			if (cur.getStartOfS() > cur.associatedNode.l) { // infeasible
				Logger.debug("Route is infeasible due to Node {000} time window {00.00} <= {00.00} <= {00.00}", k,
						cur.associatedNode.e, cur.getStartOfS(), cur.associatedNode.l);
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
			if (cur.getNumPas() > problem.capacity) { // infeasible
				Logger.debug("Route is infeasible due to Node {000} capacity {} > {} = max cap", k, cur.getNumPas(),
						problem.capacity);
				return false;
			}
			Logger.trace("RouteNode {000} is feasible", k);

		}
		// verify max ride times of all requests
		// first check max ride time of the insert
		if (insertedSolution.dropoff.getStartOfS() - (insertedSolution.pickup.getStartOfS()
				+ insertedSolution.pickup.associatedNode.s) > insertedSolution.associatedRequest.L) {
			Logger.debug("Request {000} is infeasible due max ride time {00.00} > {00.00} = L",
					insertedSolution.associatedRequest.id,
					insertedSolution.dropoff.getStartOfS()
							- (insertedSolution.pickup.getStartOfS() + insertedSolution.pickup.associatedNode.s),
					insertedSolution.associatedRequest.L);
			return false;
		} else {
			// Logger.debug("Request {000} is feasible due max ride time {00.00} < {00.00} =
			// L", insertedSolution.associatedRequest.id,
			// insertedSolution.dropoff.getStartOfS() -
			// (insertedSolution.pickup.getStartOfS() +
			// insertedSolution.pickup.getAssociatedNode().s),
			// insertedSolution.associatedRequest.L);
		}
		// TODO we can only check max ride time after insertion
		// since we need to update the timings of the routenodes in the old solution
		Solution copy = solutionWithoutRoute.copy();
		copy.replaceRouteWithLongerRoute(newRoute, copy.requests.get(insertedSolution.associatedRequest.id - 1));
		return copy.isMaxRideSatisfied();
	}

	// check capacity after insertion of pickup and dropoff
	protected boolean checkCapacity(int start, int end, Route r, int Q) {
		// first check newly inserted if necessary (i.e. not first)
		if (start > 0) {
			int prevCap = r.get(start - 1).getNumPas();
			if (prevCap + 1 > Q) {
				return false;
			}
		}
		// then rest of nodes
		for (int i = start + 1; i < end; i++) {
			RouteNode cur = r.get(i);
			if (cur.getNumPas() + 1 > Q) {
				return false;
			}
		}
		return true;
	}

	// this is different from the original SC1
	// if the two insertions are next to eachother, we do not try to set the pickup as early as possible but just find any satisfying solution
	protected boolean SC1NoTransfer(int pickupLoc, int dropoffLoc, RouteNode pickup, RouteNode dropoff, Route newRoute, Problem p) {
		double pickupS, dropoffS;
		if (pickupLoc + 1 < dropoffLoc) {
			// they are not next to eachother so simply follow rules from Masson 14
			// set pickupS and dropoffS
			RouteNode prev = newRoute.get(dropoffLoc - 1);
			dropoffS = Math.max(dropoff.associatedNode.e, prev.tightE + prev.associatedNode.s + p.distanceBetween(dropoff.associatedNode, prev.associatedNode));
			Request req = p.requests.get(pickup.requestId - 1);
			// disregard precedence constraint if it is the first
			if (pickupLoc > 0) {
				prev = newRoute.get(pickupLoc - 1);
				pickupS = Math.max(Math.max(pickup.associatedNode.e,  prev.tightE + prev.associatedNode.s + p.distanceBetween(prev.associatedNode, pickup.associatedNode)), dropoffS - req.L - pickup.associatedNode.s);
			} else {
				pickupS = Math.max(pickup.associatedNode.e, dropoffS - req.L - pickup.associatedNode.s);
			}
			if (pickupS > pickup.associatedNode.l || dropoffS > dropoff.associatedNode.l) { // this is the first time we adjust dropoff and pickup, so verify time windows
				return false;
			}
			// check arrival time after pickup
			RouteNode next = newRoute.get(pickupLoc + 1);
			if (pickupS + pickup.associatedNode.s + p.distanceBetween(pickup.associatedNode, next.associatedNode) > next.tightE) {
				return false;
			}
			// if there is a next node, check arrival time at next
			if (dropoffLoc < newRoute.size() - 1) {
				next = newRoute.get(dropoffLoc + 1);
				if (dropoffS + dropoff.associatedNode.s + p.distanceBetween(dropoff.associatedNode, next.associatedNode) > next.tightE) {
					return false;
				}
			}
		} else {
			// they are next to eachother so tightE of pickup is undefined
			// however we know that it is satisfiable so we select earliest as possible pickup and dropoff and then adjust
			// in this case we dont need to find the earliest solution for pickupS, but just any satisfying since only the second constraint on dropoffS is binding
			// (from NC1 we already know its own time window is satisfied).
			
			// calculate earliest pickupS
			if (pickupLoc == 0) {
				pickupS = pickup.associatedNode.e;
			} else {
				RouteNode prev = newRoute.get(pickupLoc - 1);
				pickupS = Math.max(pickup.associatedNode.e, prev.tightE + prev.associatedNode.s + p.distanceBetween(prev.associatedNode, pickup.associatedNode));
			}
			// calculate corresponding dropoffS
			dropoffS = pickupS + pickup.associatedNode.s + p.distanceBetween(pickup.associatedNode, dropoff.associatedNode);
			// if we start too early we adjust both the pickup and dropoff to a later point
			if (dropoffS < dropoff.associatedNode.e) {
				double dif = dropoff.associatedNode.e - dropoffS;
				dropoffS += dif;
				pickupS += dif;
			}
			
			if (pickupS > pickup.associatedNode.l || dropoffS > dropoff.associatedNode.l) { // this is the first time we adjust dropoff and pickup, so verify time windows
				return false;
			}
			
			if (dropoffLoc < newRoute.size() - 1) {
				// we have a subsequent node so we need to check the schedule
				RouteNode next = newRoute.get(dropoffLoc + 1);
				if (dropoffS + dropoff.associatedNode.s + p.distanceBetween(dropoff.associatedNode, next.associatedNode) > next.tightE) {
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * Verifies whether the insertion of a node is feasible based on tight time
	 * windows of full route i.e. NC1.
	 * 
	 * @param location
	 * @param route
	 * @param insert
	 * @param p
	 * @return
	 */
	// TODO: do own time window in seperate function so we can abort early?
	protected boolean NC1(int location, Route route, RouteNode insert, Problem p) {
		RouteNode prev = null;
		double prevS = insert.getStartOfS(); // this is set in the function verifyOwnInsertionWindow
		double newS = 0;
		prev = insert;

		// note that this is called before insertion is done
		// so the first new node is located at the current insertion location
		for (int i = location; i < route.size(); i++) {
			RouteNode cur = route.get(i);
			double arrival = prevS + prev.associatedNode.s + p.distanceBetween(prev.associatedNode, cur.associatedNode);
			newS = Math.max(arrival, cur.getStartOfS()); // cannot start earlier than current starting due to tightE
															// (which can only be higher after insertion)
			if (newS > cur.tightL) {
				// start of S is too late
				return false;
			} else if (newS <= cur.getStartOfS()) {
				// this insertion has no influence on the rest of the route so skip checking the
				// rest
				return true;
			}
			// something changed in the route so keep track
			prev = cur;
			prevS = newS;

		}
		// own + all subseq. time windows managed
		return true;
	}

	protected boolean NC0(int location, Route route, RouteNode insert, Problem p) {
		RouteNode prev = null;
		if (location > 0) {
			prev = route.get(location - 1);
			double arrival = prev.getStartOfS() + prev.associatedNode.s
					+ p.distanceBetween(insert.associatedNode, prev.associatedNode);
			if (arrival < insert.associatedNode.l) {
				insert.setStartOfS(Math.max(arrival, insert.associatedNode.e));
				return true;
			}
		} else {
			insert.setStartOfS(insert.associatedNode.e);
			return true; // always make own timewindow
		}
		return false;
	}

	// inner data class
	protected class RouteRequest {

		public Route route;
		public int requestId;
		public int routeIndex;

		public RouteRequest(Route route, int requestId, int routeIndex) {
			this.route = route;
			this.requestId = requestId;
			this.routeIndex = routeIndex;
		}
	}
}
