/**
 * 
 */
package heuristics2.destroy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.pmw.tinylog.Logger;

import problem.Problem;
import solution.Solution;

/**
 * @author Jari Meevis
 *
 */
public class RandomDestroy extends DestroyHeuristic {
	
	Random rand;
	
	public RandomDestroy(Problem problem, Random rand) {
		super(problem);
		this.rand = rand;
	}

	/* (non-Javadoc)
	 * @see heuristics2.destroy.DestroyHeuristic#destroy(solution.Solution)
	 */
	@Override
	public List<Integer> destroy(Solution s, int number) {
		List<Integer> destroyedRequestIds = new ArrayList<>();
		if (number > s.requests.size()) { 
			Logger.warn("Trying to destroy {}/{} requests. Impossible!", number, s.requests.size());
			return destroyedRequestIds; 
		}
		
		int numFails = 0;
		
		for (int i = 0; i < number; i++) {
			// randomly select a request we have not destroyed yet
			int index = rand.nextInt(s.requests.size());
			while (s.requests.get(index).pickup == null) {
				index = rand.nextInt(s.requests.size());
			}
			
			Logger.debug("Destroying request {000}", index + 1);
			
			if (!destroySpecific(s, index + 1)) {
				numFails++;
				Logger.warn("Failure (total = {}) during destruction of request {000}", numFails, index + 1);
			}
			destroyedRequestIds.add(index);

		}
		return destroyedRequestIds;
	}

}
