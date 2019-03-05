package heuristics.destroy;

import java.util.ArrayList;
import java.util.List;

import org.pmw.tinylog.Logger;

import problem.Problem;
import solution.Solution;
import solution.SolutionRequest;

public class MostExpensiveDestroy extends DestroyHeuristic {

	public MostExpensiveDestroy(Problem p) {
		super(p);
	}

	@Override
	public List<SolutionRequest> destroyMultiple(Solution currentSolution, int n) {
		if (n > currentSolution.requests.size()) {
			Logger.warn("Trying to remove {} requests, but there are only {} requests", n, currentSolution.requests.size());
			n = currentSolution.requests.size();
		}
		List<SolutionRequest> destroyed = new ArrayList<>();
		int[] toDestroy = getMostExpensiveRequests(currentSolution, n);
		for (int i = 0; i < toDestroy.length; i++) {
			destroyed.add(defaultDestroy(currentSolution, toDestroy[i]));
		}
		return destroyed;
	}

	@Override
	public SolutionRequest destroySingle(Solution currentSolution) {
		int expensiveRequestIndex = getMostExpensiveRequest(currentSolution);
		return defaultDestroy(currentSolution, expensiveRequestIndex);
	}

	@Override
	public String toString() {
		return "Destroy Heuristic: Most Expensive Request";
	}

	private int getMostExpensiveRequest(Solution solution){		
		double fullSolutionCost = solution.getCost();
		double highestSavings = Double.NEGATIVE_INFINITY;
		int indexExpensiveRequest = -1;
		for (int index = 0; index < solution.requests.size(); index++){
			Solution copy = solution.copy();
			defaultDestroy(copy, index);
			double costAfterRemoval = copy.getCost();
			double savings = fullSolutionCost - costAfterRemoval;
			if(savings > highestSavings){	
				highestSavings = savings;
				indexExpensiveRequest = index;
				Logger.debug("Current most expensive request {000} has cost {00.00}", index + 1, highestSavings);
			}
		}
		Logger.debug("Found most expensive request {000} with cost {00.00}", indexExpensiveRequest + 1, highestSavings);
		return indexExpensiveRequest;
		
	}
	
	private int[] getMostExpensiveRequests(Solution solution, int n){
		int[] indices = new int[n];
		double[] associatedSavings = new double[n];
		double fullCost = solution.getCost();
		int numFound = 0;
		int indexOfLowest = -1; // in the above array
		double minimumSavingsNeeded = Double.POSITIVE_INFINITY; // to get a place in the array of most expensive requests
		for (int i = 0; i < solution.requests.size(); i++) {
			Solution copy = solution.copy();
			defaultDestroy(copy, i);
			double costAfterRemoval = copy.getCost();
			double savings = fullCost - costAfterRemoval;
			if (numFound < n) {
				// here we just add whatever we find and keep track of the cheapest
				indices[numFound] = i;
				associatedSavings[numFound] = savings;
				numFound++;
				if (savings < minimumSavingsNeeded) {
					indexOfLowest = i;
					minimumSavingsNeeded = savings;
				}
			} else if (savings > minimumSavingsNeeded) {
				// but here we only replace if the savings are larger than the least savings
				indices[indexOfLowest] = i;
				associatedSavings[indexOfLowest] = savings;
				indexOfLowest = getIndexOfLowest(associatedSavings);
				minimumSavingsNeeded = associatedSavings[indexOfLowest];
			}
		}
		for (int i = 0; i < n; i++) {
			Logger.debug("Expensive request: {000} at cost {00.00}", indices[i] + 1, associatedSavings[i]);
		}
		return indices;
	}

	private int getIndexOfLowest(double[] arr) {
		double curLowest = arr[0];
		int curIndex = 0;
		for (int i = 1; i < arr.length; i++) {
			if (arr[i] < curLowest) {
				curLowest = arr[i];
				curIndex = i;
			}
		}
		return curIndex;
	}
}
