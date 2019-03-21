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
	
	public double CalculateRelatedness(SolutionRequest x, SolutionRequest y, Solution s){
		if(problem.distanceBetween(x.pickup.associatedNode, y.pickup.associatedNode) > 0){
			double relatedness = -1;
			double distance = problem.distanceBetween(x.pickup.associatedNode, y.pickup.associatedNode)+ problem.distanceBetween(x.dropoff.associatedNode, y.dropoff.associatedNode);
			double timeDiff = Math.abs(x.pickup.getStartOfS() - y.pickup.getStartOfS()) + Math.abs(x.dropoff.getStartOfS() - y.dropoff.getStartOfS());
			int loadDiff = Math.abs(x.pickup.getNumPas() - y.pickup.getNumPas()) + Math.abs(x.dropoff.getNumPas() - y.dropoff.getNumPas());
			//Logger.debug("Relatedness of requests {} and {}", x.id, y.id);
			//Logger.debug("Distance: {} time: {}  and passenger: {}", distance, timeDiff, y.pickup.getNumPas());
			//Logger.debug("Compared to max dist, time diff and capacity of:{} {} {}", problem.getMaxDistance(), s.latestService(), problem.capacity);
			relatedness = DIST_WEIGHT * distance/problem.getMaxDistance() + TIME_WEIGHT * timeDiff/s.latestService() + LOAD_WEIGHT * loadDiff/problem.capacity;
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
			double relatedness = CalculateRelatedness(s.requests.get(index), sr, s);
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
			Logger.debug("RequestID: {}, relatedness: {},RequestID: {}, relatedness: {},RequestID: {}, relatedness: {},RequestID: {}, relatedness: {}",
					highlyRelatedIds[0],highlyRelated[0],highlyRelatedIds[1],highlyRelated[1],highlyRelatedIds[2],highlyRelated[2],highlyRelatedIds[3],highlyRelated[3]);
		}
		destroyedRequestIds.add(index+1);
		Logger.debug("Destroyed request {}:", index+1);
		for(int k=0;k<number ; k++){
			destroyedRequestIds.add(highlyRelatedIds[k]);
			Logger.debug("Destroyed request: {}", highlyRelatedIds[k]);

		}
		return destroyedRequestIds;
	}

}