package heuristics.repair;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import org.pmw.tinylog.Logger;

import problem.Node;
import problem.Problem;
import solution.Route;
import solution.RouteNode;
import solution.RouteNodeType;
import solution.Solution;
import solution.SolutionRequest;

public class TransferFirst extends RepairHeuristic {

	private static final double CHANCE_OF_OPENING_TRANSFER = 0.7;
	Random rand;
	
	public TransferFirst(Problem problem, Random rand) {
		super(problem);
		this.rand = rand;
	}

	@Override
	public boolean repair(Solution s, List<Integer> requestIdsToRepair) {
		if (s.closedTransfers.size() > 0 && (s.openTransfers.size() == 0 || rand.nextDouble() < CHANCE_OF_OPENING_TRANSFER)) {
			openRandomTransfer(s);
		}
		List<Integer> copyOfIds = new ArrayList<>();
		copyOfIds.addAll(requestIdsToRepair);
		
		for (ListIterator<Integer> iter = requestIdsToRepair.listIterator(); iter.hasNext(); ) {
			int reqId = iter.next();
			RouteRequest[] pickupFirst = fullInsertion(s, reqId, RouteRequestType.PICKUP_AND_TRANSFER);
			RouteRequest[] dropoffFirst = fullInsertion(s, reqId, RouteRequestType.TRANSFER_AND_DROPOFF);
			RouteRequest[] noTransfer = fullInsertion(s, reqId, RouteRequestType.NO_TRANSFER);
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
			s.calcTightWindows(); // update windows
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
	
	private RouteRequest[] fullInsertion(Solution s, int requestId, RouteRequestType type) {
		Solution workingCopy = s.copy();
		Node transfer = null; // need this further down
		RouteRequest partialInsertion = null;
		
		// if we are creating a request with a transfer we need to do it in two steps
		if (type != RouteRequestType.NO_TRANSFER) {
			// find the best partial transfer
			partialInsertion = bestPartialInsertion(s, requestId, type);
			// and insert it
			transfer = partialInsertion.transfer;
			workingCopy.setRoute(partialInsertion.routeIndex, partialInsertion.route);
			workingCopy.calcTightWindows();
		}

		Solution costCalc = workingCopy.copy();
		SolutionRequest sr = workingCopy.requests.get(requestId - 1);
		
		double bestInsertionCost = Double.POSITIVE_INFINITY;
		Route bestRoute = null;
		int bestRouteIndex = -1;
		
		for (int routeIndex = 0; routeIndex < workingCopy.routes.size(); routeIndex++) {
			final Route oldRoute = costCalc.routes.get(routeIndex);
			double oldCost = oldRoute.getCost(s.p);
			
			Route newRoute = oldRoute.copy();
			// create the nodes (depending on the type of insertion)
			RouteNode dropoff = null;
			RouteNode pickup = null;
			switch (type) {
			case PICKUP_AND_TRANSFER: 
				// note that in this case we have already inserted the pickup and transfer, now we complete it
				// so we insert the other two nodes
				dropoff = new RouteNode(sr.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, requestId, oldRoute.vehicleId);
				pickup = new RouteNode(transfer, RouteNodeType.TRANSFER_PICKUP, requestId, oldRoute.vehicleId);
				break;
			case TRANSFER_AND_DROPOFF:
				dropoff = new RouteNode(transfer, RouteNodeType.TRANSFER_DROPOFF, requestId, oldRoute.vehicleId);
				pickup = new RouteNode(sr.associatedRequest.pickupNode, RouteNodeType.PICKUP, requestId, oldRoute.vehicleId);
				break;
			case NO_TRANSFER:
				dropoff = new RouteNode(sr.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, requestId, oldRoute.vehicleId);
				pickup = new RouteNode(sr.associatedRequest.pickupNode, RouteNodeType.PICKUP, requestId, oldRoute.vehicleId);
				break;
			}
			
			for (int i = 0; i < oldRoute.size() + 1; i++) {
				// check own timewindow (and abort if it cannot be managed)
				if (!NC0(i, newRoute, pickup, s.p)) {
					break;
				}
				// check timewindows of subsequent nodes
				if (!NC1(i, newRoute, pickup, s.p)) {
					continue;
				}
				newRoute.add(i, pickup);
				for (int j = i + 1; j < newRoute.size() + 1; j++) {
					// check timewindows of subsequent nodes
					if (!NC0(j, newRoute, dropoff, s.p)) {
						break;
					}
					// check capacity of all nodes
					if (!checkCapacity(i, j, newRoute, s.p.capacity)) {
						break;
					} 
					// check timewindows of subseq. nodes
					if (!NC1(j, newRoute, dropoff, s.p)) {
						continue;
					}
					newRoute.add(j, dropoff);
					// we might have a feasible solution
					// check cost, then SC1 and/or full feasibility check
					
					double newCost = newRoute.getCost(s.p);
					double insertionCost = newCost - oldCost;
					if (insertionCost < bestInsertionCost) {
						if (type == RouteRequestType.NO_TRANSFER && SC1NoTransfer(i, j, pickup, dropoff, newRoute, s.p)) {
							bestRoute = newRoute.copy();
							bestRouteIndex = routeIndex;
							bestInsertionCost = insertionCost;
							Logger.debug("Found a new best insertion: request {000} into route with index {} at cost {00.00}.", requestId, routeIndex, insertionCost);
						} else {
							costCalc.setRoute(routeIndex, newRoute);
							if (costCalc.isFeasible()) {
								bestRoute = newRoute.copy();
								bestRouteIndex = routeIndex;
								bestInsertionCost = insertionCost;
								Logger.debug("Found a new best insertion: request {000} into route with index {} at cost {00.00}.", requestId, routeIndex, insertionCost);
							}
							costCalc = workingCopy.copy();
						}
					}
					newRoute.remove(j);
				}
				newRoute.remove(i);
			}
		}
		// finally, try insertion as own route
		RouteNode dropoff = null;
		RouteNode pickup = null;
		int vehicleId = s.getNextFreeVehicleId();
		if (type == RouteRequestType.PICKUP_AND_TRANSFER) {
			dropoff = new RouteNode(sr.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, requestId, vehicleId);
			pickup = new RouteNode(transfer, RouteNodeType.TRANSFER_PICKUP, requestId, vehicleId);
		} else if (type == RouteRequestType.TRANSFER_AND_DROPOFF) {
			dropoff = new RouteNode(transfer, RouteNodeType.TRANSFER_DROPOFF, requestId, vehicleId);
			pickup = new RouteNode(sr.associatedRequest.pickupNode, RouteNodeType.PICKUP, requestId, vehicleId);
		} else {
			dropoff = new RouteNode(sr.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, requestId, vehicleId);
			pickup = new RouteNode(sr.associatedRequest.pickupNode, RouteNodeType.PICKUP, requestId, vehicleId);
		}
		Route tempRoute = new Route(vehicleId);
		tempRoute.add(pickup);
		tempRoute.add(dropoff);
		double cost = tempRoute.getCost(s.p);
		if (cost < bestInsertionCost) {
			costCalc.setRoute(-1, tempRoute);
			if (costCalc.isFeasible()) {
				bestRoute = tempRoute.copy();
				bestRouteIndex = -1;
				bestInsertionCost = cost;
				Logger.debug("Current best: add finished request {000} ({}) as new Route (cost: {00.00}).", sr.id, type, cost);
			}
//			costCalc = workingCopy.copy(); // don't need it anymore
		}
		
		if (bestRoute == null) {
			return null;
		}
		bestRoute.updateCapacity();
		
		// return result
		RouteRequestType insertedType = RouteRequestType.NO_TRANSFER;
		if (type == RouteRequestType.PICKUP_AND_TRANSFER) {
			insertedType = RouteRequestType.TRANSFER_AND_DROPOFF;
		} else if (type == RouteRequestType.TRANSFER_AND_DROPOFF) {
			insertedType = RouteRequestType.PICKUP_AND_TRANSFER;
		}
		RouteRequest finishOfInsertion = new RouteRequest(bestRoute, requestId, bestRouteIndex, insertedType);
		finishOfInsertion.transfer = transfer;
		finishOfInsertion.insertionCost = bestInsertionCost;
		RouteRequest[] result = {partialInsertion, finishOfInsertion};
		return result;
	}

	private RouteRequest bestPartialInsertion(Solution s, int requestId, RouteRequestType type) {
		if (type == RouteRequestType.NO_TRANSFER) {
			Logger.error("Do not use a partial insertion when not using a transfer!");
			return null;
		}
		Solution costCalc = s.copy();
		
		double bestInsertionCost = Double.POSITIVE_INFINITY;
		Route bestRoute = null;
		Node bestTransfer = null;
		int bestRouteIndex = -1;
		
		SolutionRequest sr = s.requests.get(requestId - 1);
		// first we insert the best pickup-transfer
		for (Node t : s.openTransfers) {
			for (int routeIndex = 0; routeIndex < s.routes.size(); routeIndex++) {
				final Route oldRoute = costCalc.routes.get(routeIndex);
				double oldCost = oldRoute.getCost(s.p);
				
				Route newRoute = oldRoute.copy();
				RouteNode dropoff, pickup;
				if (type == RouteRequestType.PICKUP_AND_TRANSFER) {
					dropoff = new RouteNode(t, RouteNodeType.TRANSFER_DROPOFF, requestId, oldRoute.vehicleId);
					pickup = new RouteNode(sr.associatedRequest.pickupNode, RouteNodeType.PICKUP, requestId, oldRoute.vehicleId);
				} else {
					dropoff = new RouteNode(sr.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, requestId, oldRoute.vehicleId);
					pickup = new RouteNode(t, RouteNodeType.TRANSFER_PICKUP, requestId, oldRoute.vehicleId);
				}
				for (int i = 0; i < oldRoute.size() + 1; i++) {
					// check own timewindow (and abort if it cannot be managed)
					if (!NC0(i, newRoute, pickup, s.p)) {
						break;
					}
					// check timewindows of subsequent nodes
					if (!NC1(i, newRoute, pickup, s.p)) {
						continue;
					}
					newRoute.add(i, pickup);
					for (int j = i + 1; j < newRoute.size() + 1; j++) {
						// check timewindows of subsequent nodes
						if (!NC0(j, newRoute, dropoff, s.p)) {
							break;
						}
						// check capacity of all nodes
						if (!checkCapacity(i, j, newRoute, s.p.capacity)) {
							break;
						} 
						// check timewindows of subseq. nodes
						if (!NC1(j, newRoute, dropoff, s.p)) {
							continue;
						}
						newRoute.add(j, dropoff);
						// we might have a feasible solution
						// check cost, then SC1 and/or full feasibility check
						
						double newCost = newRoute.getCost(s.p);
						double insertionCost = newCost - oldCost;
						if (insertionCost < bestInsertionCost) {
							costCalc.setRoute(routeIndex, newRoute);
							if (costCalc.isFeasible()) {
								bestRoute = newRoute.copy();
								bestRouteIndex = routeIndex;
								bestInsertionCost = insertionCost;
								bestTransfer = t;
								Logger.debug("Found a new best partial insertion: request {000} into route with index {} at cost {00.00}.", requestId, routeIndex, insertionCost);
							}
							costCalc = s.copy();
						}
						newRoute.remove(j);
					}
					newRoute.remove(i);
				}
			}
			// calculate the cost of inserting these in a new route (always feasible)
			RouteNode dropoff = null;
			RouteNode pickup = null;
			int vehicleId = s.getNextFreeVehicleId();
			if (type == RouteRequestType.PICKUP_AND_TRANSFER) {
				dropoff = new RouteNode(t, RouteNodeType.TRANSFER_DROPOFF, requestId, vehicleId);
				pickup = new RouteNode(sr.associatedRequest.pickupNode, RouteNodeType.PICKUP, requestId, vehicleId);
			} else if (type == RouteRequestType.TRANSFER_AND_DROPOFF) {
				dropoff = new RouteNode(sr.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, requestId, vehicleId);
				pickup = new RouteNode(t, RouteNodeType.TRANSFER_PICKUP, requestId, vehicleId);
			}
			Route tempRoute = new Route(vehicleId);
			tempRoute.add(pickup);
			tempRoute.add(dropoff);
			double cost = tempRoute.getCost(s.p);
			if (cost < bestInsertionCost) {
				costCalc.setRoute(-1, tempRoute);
				if (costCalc.isFeasible()) {
					bestRoute = tempRoute.copy();
					bestRouteIndex = -1;
					bestInsertionCost = cost;
					Logger.debug("Current best: add partial request {000} ({}) as new Route (cost: {00.00}).", sr.id, type, cost);
				}
				costCalc = s.copy();
			}
		}
		// we have inserted
		if (bestRoute == null) {
			return null;
		}
		bestRoute.updateCapacity();
		RouteRequest result = new RouteRequest(bestRoute, requestId, bestRouteIndex, type);
		result.transfer = bestTransfer;
		result.insertionCost = bestInsertionCost;
		return result;
	}
}
