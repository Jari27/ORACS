package heuristics.repair;

import java.util.List;
import org.pmw.tinylog.Logger;

import problem.Problem;
import problem.Request;
import solution.Route;
import solution.RouteNode;
import solution.Solution;
import solution.SolutionRequest;

public class GreedyNoTransferOneByOne extends RepairHeuristic {

	public GreedyNoTransferOneByOne(Problem problem) {
		super(problem);
	}
	
	@Override
	public boolean repair(Solution s, List<Integer> requestIdsToRepair) {
		//now i want to replace the route in which this solutionrequest was originally with the new one
		for(int i = 0;i <requestIdsToRepair.size() ;i++){
			int reqId = requestIdsToRepair.get(i);
			SolutionRequest sr1 = s.requests.get(reqId -1);
			findBestRoute(sr1,s); //this returns the route with the cheapest insertion for sr1, with the request already inserted, only if its feasible
			Logger.debug("Successfully inserted request {}", reqId);
			//otherwise creates a new route with the solutionrequest
		}
		return true;
	}

}
