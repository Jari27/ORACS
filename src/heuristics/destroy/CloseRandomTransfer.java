package heuristics.destroy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import problem.Node;
import problem.Problem;
import solution.Solution;
import solution.SolutionRequest;

public class CloseRandomTransfer extends DestroyHeuristic {
	
	private Random rand;
	
	public static int successes = 0;

	public CloseRandomTransfer(Problem problem, Random rand) {
		super(problem);
		this.rand = rand;
	}

	@Override
	public List<Integer> destroy(Solution s, int number) {
		List<Integer> destroyIds = new ArrayList<>();
		if (s.openTransfers.size() == 0) {
			return destroyIds;
		}
		successes++;
		
		// select random transfer
		int index = rand.nextInt(s.openTransfers.size());
		Node transfer = s.openTransfers.get(index);
		
		for (SolutionRequest r : s.requests) {
			if (r.hasTransfer() && r.transferDropoff.associatedNode == transfer) {
				destroyIds.add(r.associatedRequest.id);
				destroySpecific(s, r.associatedRequest.id);
			}
		}
		
		s.closedTransfers.add(transfer);
		s.openTransfers.remove(transfer);
		return destroyIds;
	}

}
