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
		
		for (int rIndex = 0; rIndex < requestIdsToRepair.size(); rIndex++) {
			
		}
		// TODO finish
		return false;
	}

	private CostRouteNode[] calculateFullInsertionMatrix(Solution s, List<Integer> requestIdsToRepair) {
		CostRouteNode[] result = new CostRouteNode[s.routes.size()];
		for (int i = 0; i < s.routes.size(); i++) {
			result[i] = calculateRouteInsertionCost(i, s, requestIdsToRepair);
		}
		return result;
	}

	// TODO test
	private CostRouteNode calculateRouteInsertionCost(int routeIndex, Solution s, List<Integer> requestIdsToRepair) {
		double[] costs = new double[requestIdsToRepair.size()];
		Route[] routes = new Route[requestIdsToRepair.size()];
		RouteNode[][] newNodes = new RouteNode[requestIdsToRepair.size()][2];
		
		Route oldRoute = s.routes.get(routeIndex);
		double oldCost = oldRoute.getCost(problem);
		
		Solution costCalc = s.copy();
		costCalc.calcTightWindows();
		
		for (int i = 0; i < requestIdsToRepair.size(); i++) {
			int reqId = requestIdsToRepair.get(i);
			Request assoc = problem.requests.get(reqId - 1);
			RouteNode pickup = new RouteNode(assoc.pickupNode, RouteNodeType.PICKUP, reqId, oldRoute.vehicleId);
			RouteNode dropoff = new RouteNode(assoc.dropoffNode, RouteNodeType.DROPOFF, reqId, oldRoute.vehicleId);
			newNodes[i][1] = pickup;
			newNodes[i][2] = dropoff;
			
			costs[i] = Double.POSITIVE_INFINITY;
			
			for (int j = 0; j < oldRoute.size(); j++) {
				Route insertP = s.routes.get(routeIndex).copy();
				insertP.add(j, pickup);
				if (j == 0) {
					pickup.setArrival(pickup.associatedNode.e);
					pickup.setStartOfS(pickup.associatedNode.e);
					pickup.setNumPas(1);
				} else {
					RouteNode prev = insertP.get(j-1);
					pickup.setArrival(prev.getDeparture() + problem.distanceBetween(prev.associatedNode, pickup.associatedNode));
					pickup.setStartOfS(Math.max(pickup.associatedNode.e, pickup.getArrival()));
					pickup.setNumPas(prev.getNumPas() + 1);
				}
				// very quick feasibility check (earliest window always satisfied, later insertion impossible, so check L and break).
				if (!pickup.isLFeasible()) {
					Logger.trace("Insertion of pickup of request {000} at location {} in Route {} was infeasible due to time windows", reqId, j, routeIndex);
					break;
				} if (pickup.getNumPas() > problem.capacity) {
					Logger.trace("Insertion of pickup of request {000} at location {} in Route {} was infeasible due to capacity constraints", reqId, j, routeIndex);
					continue;
				}
				// successful pickup insertion
				for (int k = j + 1; k < insertP.size(); k++) {
					Route newRoute = insertP.copy();
					newRoute.add(j, dropoff);
					
					RouteNode prev = newRoute.get(j - 1);
					dropoff.setArrival(prev.getDeparture() + problem.distanceBetween(prev.associatedNode, dropoff.associatedNode));
					dropoff.setStartOfS(Math.max(dropoff.getArrival(), dropoff.associatedNode.e));
					dropoff.setNumPas(prev.getNumPas() - 1);
					
					if (!dropoff.isLFeasible()) {
						Logger.trace("Insertion of pickup of request {000} at location {} in Route {} was infeasible due to time windows", reqId, j, routeIndex);
						break;
					}
					// maybe (?) feasible solution
					double newCost = newRoute.getCost(problem) - oldCost;
					costCalc.routes.set(routeIndex-1, newRoute);
					SolutionRequest sr = costCalc.requests.get(reqId - 1);
					sr.pickup = pickup;
					sr.dropoff = dropoff;
					if (newCost < costs[i]) { // check this first because it's cheaper
						if (costCalc.isFeasible()) {
							costs[i] = newCost;
							routes[i] = newRoute;
						}
					}
					costCalc.routes.set(routeIndex-1, oldRoute); // set costCalc back to state before insertion so we can reuse without copying
					sr.pickup = null;
					sr.dropoff = null;		
				}
			}
		}
		return new CostRouteNode(costs, routes, newNodes);
	}
	
	// data class
	private class CostRouteNode {
		
		double[] costs;
		Route[] routes;
		RouteNode[][] nodes;
		
		public CostRouteNode(double[] costs, Route[] routes, RouteNode[][] nodes) {
			this.costs=costs;
			this.routes=routes;
			this.nodes=nodes;
		}
	}
	
//	private double 

}
