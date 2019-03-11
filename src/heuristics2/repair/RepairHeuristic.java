package heuristics2.repair;

import java.util.List;
import problem.Problem;
import solution.Solution;

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
	 * @param s the solution to destroy
	 * @param number the number of request to destroy
	 * @return true if the solution was destroyed successfully, false otherwise
	 */
	public abstract boolean repair(Solution s, List<Integer> requestIdsToRepair);

}
