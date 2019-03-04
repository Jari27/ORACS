package heuristics.destroy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.pmw.tinylog.Logger;

import problem.Problem;
import solution.Solution;
import solution.SolutionRequest;

public class RandomDestroy extends DestroyHeuristic {

	Random rand;

	public RandomDestroy(Problem p, Random rand) {
		super(p);
		this.rand = rand;
	}

	@Override
	public SolutionRequest destroySingle(Solution currentSolution) {
		int index = rand.nextInt(currentSolution.requests.size());
		return defaultDestroy(currentSolution, index);
	}

	@Override
	public List<SolutionRequest> destroyMultiple(Solution currentSolution, int n) {
		if (n > currentSolution.requests.size()) {
			Logger.warn("Trying to remove {} requests, but there are only {} requests", n, currentSolution.requests.size());
			n = currentSolution.requests.size();
		}
		List<SolutionRequest> destroyed = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			destroyed.add(this.destroySingle(currentSolution));
		}
		return destroyed;
	}

	@Override
	public String toString() {
		return "Destroy Heuristic: Random Destroy";
	}
}
