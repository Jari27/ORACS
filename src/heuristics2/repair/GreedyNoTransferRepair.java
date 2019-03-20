/**
 * 
 */
package heuristics2.repair;

import java.util.List;

import org.pmw.tinylog.Logger;

import problem.Problem;
import problem.Request;
import solution.Route;
import solution.RouteNode;
import solution.RouteNodeType;
import solution.Solution;
import solution.SolutionRequest;

public class GreedyNoTransferRepair extends RepairHeuristic {
	
	Route bestRoute = null;

	public GreedyNoTransferRepair(Problem problem) {
		super(problem);
	}

	/* (non-Javadoc)
	 * @see heuristics2.repair.RepairHeuristic#repair(solution.Solution, java.util.List)
	 */
	@Override
	public boolean repair(Solution s, List<Integer> requestIdsToRepair) {
		Logger.debug("Starting reparation using greedy no transfer insert. Trying to repair {} requests", requestIdsToRepair.size());
		
		while (requestIdsToRepair.size() > 0) {
			
			RouteRequest rr = getBestInsertion(s, requestIdsToRepair);
			
			if (rr == null) {
				// unable to insert next request
				Logger.debug("Unable to insert more requests, aborting.");
				return false;
			}
			requestIdsToRepair.remove(new Integer(rr.requestId)); // have to wrap it in an Integer, otherwise it will remove the thing at index == requestId
			s.setRoute(rr.routeIndex, rr.route);
			s.calcTightWindows(); // update windows
		}
		return true;
	}
		

	private RouteRequest getBestInsertion(Solution s, List<Integer> requestIdsToRepair) {	
		Logger.debug("Finding new best insertion.");
		Solution costCalc = s.copy();
		
		double bestInsertionCost = Double.POSITIVE_INFINITY;
		Route bestRoute = null;
		int bestRequestId = -1;
		int bestRouteIndex = -1;
		
		for (int reqId : requestIdsToRepair) {
			for (int routeIndex = 0; routeIndex < s.routes.size(); routeIndex++) {
				final Route oldRoute = costCalc.routes.get(routeIndex);
				double oldCost = oldRoute.getCost(s.p);
				
				Route newRoute = oldRoute.copy();
				SolutionRequest sr = s.requests.get(reqId - 1);
				RouteNode dropoff = new RouteNode(sr.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, reqId, oldRoute.vehicleId);
				RouteNode pickup = new RouteNode(sr.associatedRequest.pickupNode, RouteNodeType.PICKUP, reqId, oldRoute.vehicleId);
				for (int i = 0; i < oldRoute.size() + 1; i++) {
					// check timewindows of subsequent nodes
					if (!feasibleInsertion(i, newRoute, pickup, s.p)) {
						continue;
					}
					newRoute.add(i, pickup);
					for (int j = i + 1; j < newRoute.size() + 1; j++) {
						// check timewindows of subsequent nodes
						if (!feasibleInsertion(j, newRoute, dropoff, s.p)) {
							continue;
						}
						// check capacity of subsequent nodes
						if (!checkCapacity(i, j, newRoute, s.p.capacity)) {
							break;
						} 
						newRoute.add(j, dropoff);
						// we might have a feasible solution
						// check cost, then SC1 and/or full feasibility check
						
						double newCost = newRoute.getCost(s.p);
						double insertionCost = newCost - oldCost;
						if (insertionCost < bestInsertionCost) {
							if (SC1NoTransfer(i, j, pickup, dropoff, newRoute, s.p)) {
								bestRoute = newRoute.copy();
								bestRouteIndex = routeIndex;
								bestInsertionCost = insertionCost;
								bestRequestId = reqId;
								Logger.debug("Found a new best insertion: request {000} into route with index {} at cost {00.00}.", reqId, routeIndex, insertionCost);
							} else {
								// full feasibility check
								// insert route and update references for feasibility check
								costCalc.setRoute(routeIndex, newRoute);
								if (costCalc.isFeasible()) {
									bestRoute = newRoute.copy();
									bestRouteIndex = routeIndex;
									bestInsertionCost = insertionCost;
									bestRequestId = reqId;
									Logger.debug("Found a new best insertion: request {000} into route with index {} at cost {00.00}.", reqId, routeIndex, insertionCost);
								}
								// reset costCalc
								// this is to prevent recalculating the windows after feasibility checking
								costCalc = s.copy();
							}
						}
						newRoute.remove(j);
					}
					newRoute.remove(i);
				}
			}
		}
		// we now know what route to insert so update its capacity at the nodes, to make it ready for insertion
		if (bestRoute != null) {
			bestRoute.updateCapacity();
			return new RouteRequest(bestRoute, bestRequestId, bestRouteIndex);
		} 
		return null;		
	}
	
	// this is different from the original SC1
	// if the two insertions are next to eachother, we do not try to set the pickup as early as possible but just find any satisfying solution
	// since the one next to it 
	private boolean SC1NoTransfer(int pickupLoc, int dropoffLoc, RouteNode pickup, RouteNode dropoff, Route newRoute, Problem p) {
		double pickupS, dropoffS;
		if (pickupLoc + 1 < dropoffLoc) {
			// they are not next to eachother so simply follow rules from Masson 14
			// set pickupS and dropoffS
			RouteNode prev = newRoute.get(dropoffLoc - 1);
			dropoffS = Math.max(dropoff.associatedNode.e, prev.tightE + prev.associatedNode.s + p.distanceBetween(dropoff.associatedNode, prev.associatedNode));
			Request req = p.requests.get(pickup.requestId - 1);
			// disregard precedence constraint if it the first
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

	// check capacity after insertion of pickup and dropoff
	private boolean checkCapacity(int start, int end, Route r, int Q) {
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

	/**
	 * Verifies whether the insertion of a node is feasible based on tight time windows of full route
	 * i.e. NC1.
	 * @param location
	 * @param route
	 * @param insert
	 * @param p
	 * @return
	 */
	// TODO: do own time window in seperate function so we can abort early?
	private boolean feasibleInsertion(int location, Route route, RouteNode insert, Problem p) {
		RouteNode prev = null;
		double prevS = 0;
		double thisS = 0;
		if (location > 0) {
			prev = route.get(location - 1);
		}
		// first we check the newly inserted
		// if it's the first it always works so we ignore the case that prev == null
		if (prev != null) {
			prevS = prev.getStartOfS();
			thisS = Math.max(insert.associatedNode.e, prevS + prev.associatedNode.s + p.distanceBetween(prev.associatedNode, insert.associatedNode));
			if (thisS > insert.associatedNode.l) {
				// cant make own time window
				return false;
			}
		} else {
			thisS = insert.associatedNode.e;
		}
		insert.setStartOfS(thisS);
		prev = insert;
		prevS = thisS;
		
		// note that this is called before insertion is done
		// so 
		for (int i = location; i < route.size(); i++) {
			RouteNode cur = route.get(i);
			double arrival = prevS + prev.associatedNode.s + p.distanceBetween(prev.associatedNode, cur.associatedNode);
			thisS = Math.max(arrival, cur.getStartOfS()); // cannot start earlier than current starting due to tightE (which can only be higher after insertion)
			if (thisS > cur.tightL) {
				// start of S is too late
				return false;
			} else if (thisS <= cur.getStartOfS()) {
				// this insertion has no influence on the rest of the route so skip checking the rest
				return true;
			}
			// something changed in the route so keep track
			prev = cur;
			prevS = thisS;
			
		}
		// own + all subseq. time windows managed
		return true;
	}
	
	// inner data class
	private class RouteRequest {
		
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
