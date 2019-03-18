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
		CostRouteNode[] crns = calculateFullInsertionMatrix(s, requestIdsToRepair);
		 // TODO this only does one
		int bestRoute = -1;
		int bestRequest = -1;
		double bestCost = Double.POSITIVE_INFINITY;
		for (int routeIndex = 0; routeIndex < crns.length; routeIndex++) {
			CostRouteNode crn = crns[routeIndex];
			for (int requestIndex = 0; requestIndex < requestIdsToRepair.size(); requestIndex++) {
				if (crn.costs[requestIndex] < bestCost) {
					bestCost = crn.costs[requestIndex];
					bestRoute = routeIndex;
					bestRequest = requestIndex;
				}
			}
		}
		
		s.routes.set(bestRoute, crns[bestRoute].routes[bestRequest]);
		// correct references
		for (RouteNode rn : s.routes.get(bestRoute)) {
			SolutionRequest sr = s.requests.get(rn.requestId - 1);
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
		s.calcTightWindows();
		return true;
	}

	private CostRouteNode[] calculateFullInsertionMatrix(Solution s, List<Integer> requestIdsToRepair) {
		CostRouteNode[] result = new CostRouteNode[s.routes.size()];
		for (int i = 0; i < s.routes.size(); i++) {
			result[i] = calculateRouteInsertionCost(i, s, requestIdsToRepair);
		}
		return result;
	}

	// TODO use tight time windows to check insertion quickly
	private CostRouteNode calculateRouteInsertionCost(int routeIndex, Solution s, List<Integer> requestIdsToRepair) {
		double[] costs = new double[requestIdsToRepair.size()];
		Route[] routes = new Route[requestIdsToRepair.size()];
		RouteNode[][] newNodes = new RouteNode[requestIdsToRepair.size()][2];
		
		Route oldRoute = s.routes.get(routeIndex);
		double oldCost = oldRoute.getCost(problem);
		
		Solution costCalc = s.copy();
		//costCalc.calcTightWindows();
		
		for (int i = 0; i < requestIdsToRepair.size(); i++) {
			int reqId = requestIdsToRepair.get(i);
			Request assoc = problem.requests.get(reqId - 1);
			RouteNode pickup = new RouteNode(assoc.pickupNode, RouteNodeType.PICKUP, reqId, oldRoute.vehicleId);
			RouteNode dropoff = new RouteNode(assoc.dropoffNode, RouteNodeType.DROPOFF, reqId, oldRoute.vehicleId);
			newNodes[i][0] = pickup;
			newNodes[i][1] = dropoff;
			
			costs[i] = Double.POSITIVE_INFINITY;
			Route newRoute = s.routes.get(routeIndex).copy();
			for (int j = 0; j < oldRoute.size(); j++) {
				newRoute.add(j, pickup);
				
				if (j == 0) {
					pickup.setArrival(pickup.associatedNode.e);
					pickup.setStartOfS(pickup.associatedNode.e);
					pickup.setNumPas(1);
				} else {
					RouteNode prev = newRoute.get(j-1);
					pickup.setArrival(prev.getDeparture() + problem.distanceBetween(prev.associatedNode, pickup.associatedNode));
					pickup.setStartOfS(Math.max(pickup.associatedNode.e, pickup.getArrival()), false);
					pickup.setNumPas(prev.getNumPas() + 1);
				}
				// very quick feasibility check (earliest window always satisfied, later insertion impossible, so check L and break).
				if (!pickup.isLFeasible() || j != oldRoute.size() - 1 && pickup.getDeparture() + problem.distanceBetween(pickup.associatedNode, oldRoute.get(j + 1).associatedNode) < oldRoute.get(j + 1).tightL) {
					Logger.trace("Insertion of pickup of request {000} at location {} in Route {} was infeasible due to time windows", reqId, j, routeIndex);
					break;
				} if (pickup.getNumPas() > problem.capacity) {
					Logger.trace("Insertion of pickup of request {000} at location {} in Route {} was infeasible due to capacity constraints", reqId, j, routeIndex);
					continue;
				}
				// successful pickup insertion
				for (int k = j + 1; k < oldRoute.size() + 1; k++) {
					//Route newRoute = insertP.copy();
					newRoute.add(k, dropoff);
					
					RouteNode prev = newRoute.get(k - 1);
					dropoff.setArrival(prev.getDeparture() + problem.distanceBetween(prev.associatedNode, dropoff.associatedNode));
					dropoff.setStartOfS(Math.max(dropoff.getArrival(), dropoff.associatedNode.e), false);
					dropoff.setNumPas(prev.getNumPas() - 1);
					
					if (!dropoff.isLFeasible()) {
						Logger.trace("Insertion of pickup of request {000} at location {} in Route {} was infeasible due to time windows", reqId, j, routeIndex);
						newRoute.remove(k);
						break;
					}
					// maybe (?) feasible solution
					double newCost = newRoute.getCost(problem) - oldCost;
					costCalc.routes.set(routeIndex, newRoute);
					for (RouteNode rn : newRoute) { // update references for feasib. checking
						SolutionRequest sr = costCalc.requests.get(rn.requestId - 1);
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
					SolutionRequest sr = costCalc.requests.get(reqId - 1);
					sr.pickup = pickup;
					sr.dropoff = dropoff;
					if (newCost < costs[i]) { // check this first because it's cheaper
						if (costCalc.isFeasible()) {
							costs[i] = newCost;
							routes[i] = newRoute;
						}
					}
					// reset costCalc
					costCalc.routes.set(routeIndex, oldRoute); // set costCalc back to state before insertion so we can reuse without copying
					for (RouteNode rn : newRoute) { // update references for feasib. checking
						SolutionRequest srCur = costCalc.requests.get(rn.requestId - 1);
						switch (rn.type) {
						case PICKUP:
							srCur.pickup = rn;
							break;
						case DROPOFF:
							srCur.dropoff = rn;
							break;
						case TRANSFER_PICKUP:
							srCur.transferPickup = rn;
							break;
						case TRANSFER_DROPOFF:
							srCur.transferDropoff = rn;
							break;
						}
					}
					sr.pickup = null;
					sr.dropoff = null;	
					newRoute.remove(k);
				}
				newRoute.remove(j);
			}
		}
		return new CostRouteNode(costs, routes);
	}
	
	// data class
	private class CostRouteNode {
		
		double[] costs;
		Route[] routes;
		
		public CostRouteNode(double[] costs, Route[] routes) {
			this.costs=costs;
			this.routes=routes;
		}
	}
	
//	private double 

}
