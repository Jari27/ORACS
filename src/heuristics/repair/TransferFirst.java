package heuristics.repair;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import org.pmw.tinylog.Logger;

import heuristics.destroy.DestroyHeuristic;
import problem.Problem;
import solution.Solution;

public class TransferFirst extends RepairHeuristic {

	private static final double CHANCE_OF_OPENING_TRANSFER = 0.7;
	private DestroyHeuristic dh;

	public TransferFirst(Problem problem, Random r, DestroyHeuristic dh) {
		super(problem, r);
		this.dh = dh;
	}

	@Override
	public boolean repair(Solution s, List<Integer> requestIdsToRepair) {
		Logger.debug("Transfer first");
		if (s.closedTransfers.size() > 0 && (s.openTransfers.size() == 0 || r.nextDouble() < CHANCE_OF_OPENING_TRANSFER)) {
			//openRandomTransfer(s);
			openBestTransfer(s, requestIdsToRepair, 2);
		}

		List<Double> insertionCosts = new ArrayList<>();
		
		for (ListIterator<Integer> iter = requestIdsToRepair.listIterator(); iter.hasNext(); ) {
			int reqId = iter.next();
			RouteRequest[] pickupFirst = getBestFullInsertion(s, reqId, RouteRequestType.PICKUP_AND_TRANSFER);
			RouteRequest[] dropoffFirst = getBestFullInsertion(s, reqId, RouteRequestType.TRANSFER_AND_DROPOFF);
			double insertionCost = -1;
			if (pickupFirst == null && dropoffFirst == null) {
				RouteRequest[] noTransfer = getBestFullInsertion(s, reqId, RouteRequestType.NO_TRANSFER);
				s.setRoute(noTransfer[1].routeIndex, noTransfer[1].route);
			}
			else if (pickupFirst == null) {
				s.setRoute(dropoffFirst[0].routeIndex, dropoffFirst[0].route);
				s.setRoute(dropoffFirst[1].routeIndex, dropoffFirst[1].route);
				insertionCost = dropoffFirst[0].insertionCost + dropoffFirst[1].insertionCost;
			}
			else if (dropoffFirst == null) {
				s.setRoute(pickupFirst[0].routeIndex, pickupFirst[0].route);
				s.setRoute(pickupFirst[1].routeIndex, pickupFirst[1].route);
				insertionCost = pickupFirst[0].insertionCost + pickupFirst[1].insertionCost;
			} else {
				if (pickupFirst[0].insertionCost + pickupFirst[1].insertionCost > dropoffFirst[0].insertionCost + dropoffFirst[1].insertionCost) {
					s.setRoute(dropoffFirst[0].routeIndex, dropoffFirst[0].route);
					s.setRoute(dropoffFirst[1].routeIndex, dropoffFirst[1].route);
					insertionCost = dropoffFirst[0].insertionCost + dropoffFirst[1].insertionCost;
				} else {
					s.setRoute(pickupFirst[0].routeIndex, pickupFirst[0].route);
					s.setRoute(pickupFirst[1].routeIndex, pickupFirst[1].route);
					insertionCost = pickupFirst[0].insertionCost + pickupFirst[1].insertionCost;
				}
			}
			if(!s.calcTightWindows()) { // update windows
				Logger.warn("Apparent accepted solutions is unfeasible");
			}
			insertionCosts.add(insertionCost);
			
		}
		// finally, recalculate them without forces transfer
		// TODO iteratively remove
		for (int i = 0; i < requestIdsToRepair.size(); i++) {
			double oldCost = insertionCosts.get(i);
			if (oldCost < 0) {
				// does not use a transfer
				continue;
			}
			else {
				int id = requestIdsToRepair.get(i);
				Solution copy = s.copy();
				dh.destroySpecific(copy, id, false); 
				RouteRequest[] noTransfer = getBestFullInsertion(copy, id, RouteRequestType.NO_TRANSFER);
				if (noTransfer != null && noTransfer[1].insertionCost < oldCost) {
					dh.destroySpecific(s, id, false);
					s.setRoute(noTransfer[1].routeIndex, noTransfer[1].route);
					s.calcTightWindows();
				}
			}
		}
		removeUselessTransfers(s);
		return true;
	}

}
