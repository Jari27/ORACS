package heuristics2.destroy;
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
			//we need the largest possible values to be able to scale the variables
			int loadDiff = Math.abs(x.pickup.getNumPas() - y.pickup.getNumPas()) + Math.abs(x.dropoff.getNumPas() - y.dropoff.getNumPas());
			Logger.debug("Relatedness of requests {} and {}", x.id, y.id);
			Logger.debug("Distance: {} time: {}  and passenger: {}", distance, timeDiff, y.pickup.getNumPas());
			Logger.debug("Compared to max dist, time diff and capacity of:{} {} {}", problem.getMaxDistance(), s.latestService(), problem.capacity);
			relatedness = DIST_WEIGHT * distance/problem.getMaxDistance() + TIME_WEIGHT * timeDiff/s.latestService() + LOAD_WEIGHT * loadDiff/problem.capacity;
			Logger.debug("Relatedness: {}. Contribution of distance, time and load: {} and {} and {} ", relatedness, distance/problem.getMaxDistance(), timeDiff/s.latestService(), loadDiff/problem.capacity);
			return relatedness;
		}else{
			return 10;
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
		Logger.debug("We randomly selected request {} to calculate the relatedness with other requests..",index+1);
		for(SolutionRequest sr : s.requests){
			CalculateRelatedness(s.requests.get(index), sr, s);
		}
		//cannot calculate relatedness if request is currently unhandled
		return destroyedRequestIds;
	}

}
