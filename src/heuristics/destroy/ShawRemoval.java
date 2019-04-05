package heuristics.destroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.pmw.tinylog.Logger;
import problem.Problem;
import solution.Solution;
import solution.SolutionRequest;


public class ShawRemoval extends DestroyHeuristic{
	Random rand;
	
	private static final double DIST_WEIGHT = 1;
	private static final double TIME_WEIGHT = 1;
	private static final double LOAD_WEIGHT = 1;

	
	public ShawRemoval(Problem problem, Random rand){
		super(problem);
		this.rand = rand;
	}
	
	public double calcRelatedness(SolutionRequest x, SolutionRequest y, Solution s){
		if(problem.distanceBetween(x.pickup.associatedNode, y.pickup.associatedNode) > 0){
			double relatedness = -1;
			double distance = problem.distanceBetween(x.pickup.associatedNode, y.pickup.associatedNode)+ problem.distanceBetween(x.dropoff.associatedNode, y.dropoff.associatedNode);
			double timeDiff = Math.abs(x.pickup.getStartOfS() - y.pickup.getStartOfS()) + Math.abs(x.dropoff.getStartOfS() - y.dropoff.getStartOfS());
			int loadDiff = Math.abs(x.pickup.getNumPas() - y.pickup.getNumPas()) + Math.abs(x.dropoff.getNumPas() - y.dropoff.getNumPas());
			//Logger.debug("Relatedness of requests {} and {}", x.id, y.id);
			//Logger.debug("Distance: {} time: {}  and passenger: {}", distance, timeDiff, y.pickup.getNumPas());
			//Logger.debug("Compared to max dist, time diff and capacity of:{} {} {}", problem.getMaxDistance(), s.latestService(), problem.capacity);
			relatedness = DIST_WEIGHT * problem.travelCost * distance/problem.maxCost + TIME_WEIGHT * timeDiff/s.latestService() + LOAD_WEIGHT * loadDiff/problem.capacity;
			//Logger.debug("Relatedness: {}. Contribution of distance, time and load: {} and {} and {} ", relatedness, distance/problem.getMaxDistance(), timeDiff/s.latestService(), loadDiff/problem.capacity);
			return relatedness;
		}else{
			return -10;
		}

	}
	

	@Override
	public List<Integer> destroy(Solution s, int number) {
		List<Integer> destroyedRequestIds = new ArrayList<>();
		if (number > s.requests.size()) { 
			Logger.warn("Trying to destroy {}/{} requests. Impossible!", number, s.requests.size());
			return destroyedRequestIds; 
		}
		int index = rand.nextInt(s.requests.size());
		Logger.debug("We randomly selected request {} to select the {} requests with the highest relatedness and destroy them.",index+1, number);
		int[] highlyRelatedIds = new int[number];
		double[] highlyRelated = new double[number];
		double lowestRelatedness = 0; 
		int lowestRelatednessIndex = 0;
		for(SolutionRequest sr : s.requests){
			double relatedness = calcRelatedness(s.requests.get(index), sr, s);
			Logger.debug("This is request {}, it has relatedness {}", sr.id, relatedness);
			//double requestIDLowestRelatedness = highlyRelated[0][lowestRelatednessIndex];
			lowestRelatedness = 10;
			for(int i = 0; i< number;i++){
				if(highlyRelated[i] <= lowestRelatedness){
					lowestRelatedness = highlyRelated[i];
					lowestRelatednessIndex = i;
				}
			}
			Logger.debug("Lowest position is: {}, the lowest relatedness is: {}",lowestRelatednessIndex+1 ,lowestRelatedness);
			if(relatedness > lowestRelatedness){ 
				highlyRelatedIds[lowestRelatednessIndex] = sr.id;
				highlyRelated[lowestRelatednessIndex] = relatedness;
			}
		}
		//destroy the randomly chosen request
		if (!destroySpecific(s, index + 1)) {
			Logger.warn("Failure during destruction of request {000}", index + 1);
		}
		destroyedRequestIds.add(index+1);
		Logger.debug("Destroyed request {}:", index+1);
		//destroy the related requests
		for(int k=0;k<number ; k++){
			if (!destroySpecific(s, highlyRelatedIds[k])) {
				Logger.warn("Failure during destruction of request {000}", highlyRelatedIds[k]);
			}
			destroyedRequestIds.add(highlyRelatedIds[k]);
			Logger.debug("Destroyed request: {}", highlyRelatedIds[k]);
			
		}
		Logger.info("Doing a Shaw Removal..");
		return destroyedRequestIds;
	}

}
