package heuristics.repair;

import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import org.pmw.tinylog.Logger;

import problem.Node;
import problem.Problem;
import solution.Solution;

public class BestInsertionWithTransfer extends RepairHeuristic {

	private static final double CHANCE_OF_OPENING_TRANSFER = 0.7;
	
	public BestInsertionWithTransfer(Problem problem, Random r) {
		super(problem, r);
	}

	@Override
	public boolean repair(Solution s, List<Integer> requestIdsToRepair) {
		if (s.closedTransfers.size() > 0 && (s.openTransfers.size() == 0 || r.nextDouble() < CHANCE_OF_OPENING_TRANSFER)) {
			//openRandomTransfer(s);
			openBestTransfer(s, requestIdsToRepair, 2);
		}
//		List<Integer> copyOfIds = new ArrayList<>();
//		copyOfIds.addAll(requestIdsToRepair);
		
		for (ListIterator<Integer> iter = requestIdsToRepair.listIterator(); iter.hasNext(); ) {
			int reqId = iter.next();
			RouteRequest[] pickupFirst = getBestFullInsertion(s, reqId, RouteRequestType.PICKUP_AND_TRANSFER);
			RouteRequest[] dropoffFirst = getBestFullInsertion(s, reqId, RouteRequestType.TRANSFER_AND_DROPOFF);
			RouteRequest[] noTransfer = getBestFullInsertion(s, reqId, RouteRequestType.NO_TRANSFER);
			double[] cost = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
			if (pickupFirst == null && dropoffFirst == null && noTransfer == null) {
				return false;
			}
			if (pickupFirst != null) {
				cost[0] = pickupFirst[0].insertionCost + pickupFirst[1].insertionCost;
			} 
			if (dropoffFirst != null) {
				cost[1] = dropoffFirst[0].insertionCost + dropoffFirst[1].insertionCost;
			}
			if (noTransfer != null) {
				cost[2] = noTransfer[1].insertionCost;
			}
			int lowIndex = 2;
			double lowCost = cost[2] - 1e-10;
			for (int i = 0; i < cost.length - 1; i++) {
				if (cost[i] < lowCost) { // use <= to ensure we use no transfer if it's cheaper
					lowCost = cost[i];
					lowIndex = i;
				}
			}
			switch (lowIndex) {
			case 0:
				s.setRoute(pickupFirst[0].routeIndex, pickupFirst[0].route);
				s.setRoute(pickupFirst[1].routeIndex, pickupFirst[1].route);
				break;
			case 1:
				s.setRoute(dropoffFirst[0].routeIndex, dropoffFirst[0].route);
				s.setRoute(dropoffFirst[1].routeIndex, dropoffFirst[1].route);
				break;
			case 2:
				s.setRoute(noTransfer[1].routeIndex, noTransfer[1].route);
			}
			if(!s.calcTightWindows()) { // update windows
				Logger.warn("Apparent accepted solutions is unfeasible");
			}
			iter.remove(); 
		}
		removeUselessTransfers(s);
		return true;
	}

	@SuppressWarnings("unused")
	private Node openRandomTransfer(Solution s) {
		int index = r.nextInt(s.closedTransfers.size());	
		Node transfer = s.closedTransfers.remove(index);
		s.openTransfers.add(transfer);
		Logger.info("Opening transfer facility {}", transfer.id);
		return transfer;
	}


}
