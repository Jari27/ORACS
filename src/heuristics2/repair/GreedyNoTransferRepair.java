/**
 * 
 */
package heuristics2.repair;

import java.util.List;

import problem.Problem;
import solution.Route;
import solution.Solution;

public class GreedyNoTransferRepair extends RepairHeuristic {

	public GreedyNoTransferRepair(Problem problem) {
		super(problem);
	}

	/* (non-Javadoc)
	 * @see heuristics2.repair.RepairHeuristic#repair(solution.Solution, java.util.List)
	 */
	@Override
	public boolean repair(Solution s, List<Integer> requestIdsToRepair) {
		double[][] costs = calculateFullInsertionMatrix(s, requestIdsToRepair);
		
		// TODO finish
		return false;
	}

	private double[][] calculateFullInsertionMatrix(Solution s, List<Integer> requestIdsToRepair) {
		double[][] result = new double[s.routes.size()][requestIdsToRepair.size()];
		for (int i = 0; i < s.routes.size(); i++) {
			result[i] = calculateRouteInsertionCost(i, s, requestIdsToRepair);
		}
		return result;
	}

	private double[] calculateRouteInsertionCost(int routeIndex, Solution s, List<Integer> requestIdsToRepair) {
		double[] costs = new double[requestIdsToRepair.size()];
		double oldCost = s.routes.get(routeIndex).getCost(problem);
		for (int i = 0; i < requestIdsToRepair.size(); i++) {
			costs[i] = Double.POSITIVE_INFINITY;
			Route copy = s.routes.get(routeIndex).copy();
			
			// TODO finish
			
		}
		return null;
	}
	
	// note we can use NC1 and SC1 for quick feasibility checking; it can be done per route (except transfers lmao)
	
//	private double 

}
