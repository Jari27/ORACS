package heuristics.repair;

import problem.Problem;
import solution.Solution;
import solution.SolutionRequest;

public abstract class RepairHeuristic {
	
	protected Problem problem;
	
	public RepairHeuristic(Problem p) {
		this.problem = p;
	}
	
	public abstract boolean repairSingle(SolutionRequest toInsert, Solution currentSol);
	
	@Override
	public abstract String toString();
}
