/**
 * 
 */
package heuristics2.destroy;

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
	
	public RandomDestroy(Problem p, Random r) {
		super(p);
		this.rand = r;
	}

	/* (non-Javadoc)
	 * @see heuristics2.destroy.DestroyHeuristic#destroy(solution.Solution)
	 */
	@Override
	public boolean destroy(Solution s, int number) {
		if (number > s.requests.size()) { 
			return false; 
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

		}
		return numFails == 0;
	}

}
