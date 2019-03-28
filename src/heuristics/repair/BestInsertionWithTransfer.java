package heuristics.repair;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import org.pmw.tinylog.Logger;

import heuristics.repair.RepairHeuristic.RouteRequest;
import problem.Node;
import problem.Problem;
import solution.RouteNode;
import solution.Solution;
import solution.SolutionRequest;

public class BestInsertionWithTransfer extends RepairHeuristic {

	private static final double CHANCE_OF_OPENING_TRANSFER = 0.7;
	Random rand;
	
	public BestInsertionWithTransfer(Problem problem, Random rand) {
		super(problem);
		this.rand = rand;
	}

	@Override
	public boolean repair(Solution s, List<Integer> requestIdsToRepair) {
		if (s.closedTransfers.size() > 0 && (s.openTransfers.size() == 0 || rand.nextDouble() < CHANCE_OF_OPENING_TRANSFER)) {
			//openRandomTransfer(s);
			openBestTransfer(s, requestIdsToRepair);
		}
		List<Integer> copyOfIds = new ArrayList<>();
		copyOfIds.addAll(requestIdsToRepair);
		
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
			int lowIndex = 0;
			double lowCost = cost[0];
			for (int i = 1; i < cost.length; i++) {
				if (cost[i] <= lowCost) { // use <= to ensure we use no transfer if it's cheaper
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
	
	private void removeUselessTransfers(Solution s) {
		Logger.debug("Post-processing: removing useless transfers");
		List<Node> copyOfOpenTransfers = new ArrayList<>();
		copyOfOpenTransfers.addAll(s.openTransfers);
		
		for (SolutionRequest sr : s.requests) {
			if (sr.hasTransfer()) {
				copyOfOpenTransfers.remove(sr.transferDropoff.associatedNode);
			}
		}
		
		for (Node transfer : copyOfOpenTransfers) {
			Logger.debug("Closed transfer {} because it is unused.", transfer);
			s.openTransfers.remove(transfer);
			s.closedTransfers.add(transfer);
		}
		
	}

	private Node openRandomTransfer(Solution s) {
		int index = rand.nextInt(s.closedTransfers.size());	
		Node transfer = s.closedTransfers.remove(index);
		s.openTransfers.add(transfer);
		return transfer;
	}
	
	private Node openBestTransfer(Solution s, List<Integer> requestIdsToRepair){
		Node[] nodesToInsert = new Node[requestIdsToRepair.size()];
		int index = 0;
		for (ListIterator<Integer> iter = requestIdsToRepair.listIterator(); iter.hasNext(); ) {
			int reqId = iter.next();
			for(SolutionRequest sr: s.requests){
				if(reqId == sr.id){
					nodesToInsert[index] = sr.pickup.associatedNode;
					nodesToInsert[index+1] = sr.dropoff.associatedNode;
				}
			}
			
		}
		Logger.info("Hi im trying to open the best transfer location..");
		int openingTransferIndex = 1;
		double lowestDistance = 10000;
		for(Node trLoc: s.closedTransfers){
			double distance = 0;
			for(int i = 0; i< nodesToInsert.length;i++){
				distance = distance + problem.distanceBetween(trLoc, nodesToInsert[i]);
				
			}
			if(distance < lowestDistance){
				lowestDistance = distance;
				openingTransferIndex = trLoc.id;
			}
		}
		
		
		Node transfer = s.closedTransfers.remove(openingTransferIndex);
		s.openTransfers.add(transfer);
		return transfer;
	}

}
