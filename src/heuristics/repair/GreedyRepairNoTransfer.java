package heuristics.repair;

import org.pmw.tinylog.Logger;

import problem.Problem;
import solution.Route;
import solution.RouteNode;
import solution.RouteNodeType;
import solution.Solution;
import solution.SolutionRequest;

public class GreedyRepairNoTransfer extends RepairHeuristic {

	public GreedyRepairNoTransfer(Problem p) {
		super(p);
	}

	public boolean handleInsertionOfRoute(Route route, SolutionRequest toInsert, Solution currentSol) {
		// we found the cheapest route
		// if bestRoute != null ...
		//s.requests.add(toInsert);
		if (route != null) {
			Logger.debug("Best result: inserting Request {000} into Route {000}.", toInsert.associatedRequest.id, route.vehicleId);
			currentSol.replaceRouteWithLongerRoute(route, toInsert);
			return true;
		} else {
			Logger.debug("Could not insert Request {000} in Solution; no feasible insertions.", toInsert.associatedRequest.id);
			return false;
		}
	}

	@Override
	public boolean repairSingle(SolutionRequest toInsert, Solution currentSol) {
		Route bestRoute = this.findBestRoute(toInsert, currentSol);
		return handleInsertionOfRoute(bestRoute, toInsert, currentSol);
	}

	@Override
	public String toString() {
		return "Repair Heuristic: Greedy - No Transfer";
	}

}
