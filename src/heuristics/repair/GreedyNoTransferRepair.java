/**
 * 
 */
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
			
			RouteRequest rr = getBestNoTransferInsertion(s, requestIdsToRepair);
			
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
		

	private RouteRequest getBestNoTransferInsertion(Solution s, List<Integer> requestIdsToRepair) {	
		Logger.debug("Finding new best insertion.");
		Solution costCalc = s.copy();
		
		double bestInsertionCost = Double.POSITIVE_INFINITY;
		Route bestRoute = null;
		int bestRequestId = -1;
		int bestRouteIndex = -1;
		
		for (int reqId : requestIdsToRepair) {
			SolutionRequest sr = s.requests.get(reqId - 1);
			for (int routeIndex = 0; routeIndex < s.routes.size(); routeIndex++) {
				final Route oldRoute = costCalc.routes.get(routeIndex);
				double oldCost = oldRoute.getCost(s.p);
				
				Route newRoute = oldRoute.copy();
				RouteNode dropoff = new RouteNode(sr.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, reqId, oldRoute.vehicleId);
				RouteNode pickup = new RouteNode(sr.associatedRequest.pickupNode, RouteNodeType.PICKUP, reqId, oldRoute.vehicleId);
				for (int i = 0; i < oldRoute.size() + 1; i++) {
					// check own timewindow (and abort if it cannot be managed)
					if (!NC0(i, newRoute, pickup, s.p)) {
						break;
					}
					// check timewindows of subsequent nodes
					if (!NC1(i, newRoute, pickup, s.p)) {
						continue;
					}
					newRoute.add(i, pickup);
					for (int j = i + 1; j < newRoute.size() + 1; j++) {
						// check timewindows of subsequent nodes
						if (!NC0(j, newRoute, dropoff, s.p)) {
							break;
						}
						// check capacity of all nodes
						if (!checkCapacity(i, j, newRoute, s.p.capacity)) {
							break;
						} 
						// check timewindows of subseq. nodes
						if (!NC1(j, newRoute, dropoff, s.p)) {
							continue;
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
	
	
}
