package heuristics.repair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.pmw.tinylog.Logger;

import problem.Node;
import problem.Problem;
import problem.Request;
import solution.Route;
import solution.RouteNode;
import solution.RouteNodeType;
import solution.Solution;
import solution.SolutionRequest;

/**
 * @author Jari Meevis
 *
 */
public abstract class RepairHeuristic {

	Problem problem;
	Random r;

	public RepairHeuristic(Problem problem, Random r) {
		this.problem = problem;
		this.r = r;
	}

	/**
	 * Destroys a solution in place
	 * 
	 * @param s      the solution to destroy
	 * @param number the number of request to destroy
	 * @return true if the solution was destroyed successfully, false otherwise
	 */
	public abstract boolean repair(Solution s, List<Integer> requestIdsToRepair);

	// check capacity after insertion of pickup and dropoff
	protected boolean checkCapacity(int start, int end, Route r, int Q) {
		// first check newly inserted if necessary (i.e. not first)
		if (start > 0) {
			int prevCap = r.get(start - 1).getNumPas();
			if (prevCap + 1 > Q) {
				return false;
			}
		}
		// then rest of nodes
		for (int i = start + 1; i < end; i++) {
			RouteNode cur = r.get(i);
			if (cur.getNumPas() + 1 > Q) {
				return false;
			}
		}
		return true;
	}
	
	protected boolean SC1NoTransfer(int pickupLoc, int dropoffLoc, RouteNode pickup, RouteNode dropoff, Route newRoute, Problem p) {
		return SC1(pickupLoc, dropoffLoc, pickup, dropoff, newRoute, p);
	}
	// this is different from the original SC1
	// if the two insertions are next to each other, we do not try to set the pickup as early as possible but just find any satisfying solution
	private boolean SC1(int pickupLoc, int dropoffLoc, RouteNode pickup, RouteNode dropoff, Route newRoute, Problem p) {
		double pickupS, dropoffS;
		if (pickupLoc + 1 < dropoffLoc) {
			// they are not next to eachother so simply follow rules from Masson 14
			// set pickupS and dropoffS
			RouteNode prev = newRoute.get(dropoffLoc - 1);
			dropoffS = Math.max(dropoff.associatedNode.e, prev.tightE + prev.associatedNode.s + p.distanceBetween(dropoff.associatedNode, prev.associatedNode));
			Request req = p.requests.get(pickup.requestId - 1);
			// disregard precedence constraint if it is the first
			if (pickupLoc > 0) {
				prev = newRoute.get(pickupLoc - 1);
				pickupS = Math.max(Math.max(pickup.associatedNode.e,  prev.tightE + prev.associatedNode.s + p.distanceBetween(prev.associatedNode, pickup.associatedNode)), dropoffS - req.L - pickup.associatedNode.s);
			} else {
				pickupS = Math.max(pickup.associatedNode.e, dropoffS - req.L - pickup.associatedNode.s);
			}
			if (pickupS > pickup.associatedNode.l || dropoffS > dropoff.associatedNode.l) { // this is the first time we adjust dropoff and pickup, so verify time windows
				return false;
			}
			// check arrival time after pickup
			RouteNode next = newRoute.get(pickupLoc + 1);
			if (pickupS + pickup.associatedNode.s + p.distanceBetween(pickup.associatedNode, next.associatedNode) > next.tightE) {
				return false;
			}
			// if there is a next node, check arrival time at next
			if (dropoffLoc < newRoute.size() - 1) {
				next = newRoute.get(dropoffLoc + 1);
				if (dropoffS + dropoff.associatedNode.s + p.distanceBetween(dropoff.associatedNode, next.associatedNode) > next.tightE) {
					return false;
				}
			}
		} else {
			// they are next to eachother so tightE of pickup is undefined
			// however we know that it is satisfiable so we select earliest as possible pickup and dropoff and then adjust
			// in this case we dont need to find the earliest solution for pickupS, but just any satisfying since only the second constraint on dropoffS is binding
			// (from NC1 we already know its own time window is satisfied).
			
			// calculate earliest pickupS
			if (pickupLoc == 0) {
				pickupS = pickup.associatedNode.e;
			} else {
				RouteNode prev = newRoute.get(pickupLoc - 1);
				pickupS = Math.max(pickup.associatedNode.e, prev.tightE + prev.associatedNode.s + p.distanceBetween(prev.associatedNode, pickup.associatedNode));
			}
			// calculate corresponding dropoffS
			dropoffS = pickupS + pickup.associatedNode.s + p.distanceBetween(pickup.associatedNode, dropoff.associatedNode);
			// if we start too early we adjust both the pickup and dropoff to a later point
			if (dropoffS < dropoff.associatedNode.e) {
				double dif = dropoff.associatedNode.e - dropoffS;
				dropoffS += dif;
				pickupS += dif;
			}
			
			if (pickupS > pickup.associatedNode.l || dropoffS > dropoff.associatedNode.l) { // this is the first time we adjust dropoff and pickup, so verify time windows
				return false;
			}		
					
			if (dropoffLoc < newRoute.size() - 1) {
				// we have a subsequent node so we need to check the schedule
				RouteNode next = newRoute.get(dropoffLoc + 1);
				if (dropoffS + dropoff.associatedNode.s + p.distanceBetween(dropoff.associatedNode, next.associatedNode) > next.tightE) {
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * Verifies whether the insertion of a node is feasible based on tight time
	 * windows of full route i.e. NC1.
	 * 
	 * @param location
	 * @param route
	 * @param insert
	 * @param p
	 * @return
	 */
	protected boolean NC1(int location, Route route, RouteNode insert, Problem p) {
		RouteNode prev = null;
		double prevS = insert.getStartOfS(); // this is set in the function verifyOwnInsertionWindow
		double newS = 0;
		prev = insert;

		// note that this is called before insertion is done
		// so the first new node is located at the current insertion location
		for (int i = location; i < route.size(); i++) {
			RouteNode cur = route.get(i);
			double arrival = prevS + prev.associatedNode.s + p.distanceBetween(prev.associatedNode, cur.associatedNode);
			newS = Math.max(arrival, cur.getStartOfS()); // cannot start earlier than current starting due to tightE
															// (which can only be higher after insertion)
			if (newS > cur.tightL) {
				// start of S is too late
				return false;
			} else if (newS <= cur.getStartOfS()) {
				// this insertion has no influence on the rest of the route so skip checking the
				// rest
				return true;
			}
			// something changed in the route so keep track
			prev = cur;
			prevS = newS;

		}
		// own + all subseq. time windows managed
		return true;
	}

	protected boolean NC0(int location, Route route, RouteNode insert, Problem p) {
		RouteNode prev = null;
		if (location > 0) {
			prev = route.get(location - 1);
			double arrival = prev.getStartOfS() + prev.associatedNode.s
					+ p.distanceBetween(insert.associatedNode, prev.associatedNode);
			if (insert.isTransfer()) {
				// check transfer time window differently
				// i.e. check that after doing transfer & travel from transfer to dropoff
				// we can make dropoff
				Request assocReq = p.requests.get(insert.requestId - 1);
				if (arrival + insert.associatedNode.s + p.distanceBetween(insert.associatedNode, assocReq.dropoffNode) > assocReq.dropoffNode.l) {
					return false;
				}
			}
			if (arrival < insert.associatedNode.l) {
				insert.setStartOfS(Math.max(arrival, insert.associatedNode.e));
				return true;
			}
		} else {
			insert.setStartOfS(insert.associatedNode.e);
			return true; // always make own timewindow
		}
		return false;
	}

	protected RouteRequest[] getBestFullInsertion(Solution s, int requestId, RouteRequestType type) {
		Solution workingCopy = s.copy();
		Node transfer = null; // need this further down
		RouteRequest partialInsertion = null;
		
		// if we are creating a request with a transfer we need to do it in two steps
		if (type != RouteRequestType.NO_TRANSFER) {
			// find the best partial transfer
			partialInsertion = getBestPartialInsertion(s, requestId, type);
			if (partialInsertion == null) {
				return null;
			}
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
						boolean isFeasible = false;
						if (type == RouteRequestType.NO_TRANSFER) {
							isFeasible = SC1NoTransfer(i, j, pickup, dropoff, newRoute, s.p);
						}
						if (isFeasible) {
							bestRoute = newRoute.copy();
							bestRouteIndex = routeIndex;
							bestInsertionCost = insertionCost;
							Logger.debug("Found a new best insertion: request {000} into route with index {} at cost {00.00}.", requestId, routeIndex, insertionCost);
						} else {
							costCalc.setRoute(routeIndex, newRoute.copy());
							if (costCalc.isFeasible()) {
								bestRoute = newRoute.copy();
								bestRouteIndex = routeIndex;
								bestInsertionCost = insertionCost;
								Logger.debug("Found a new best insertion: request {000} into route with index {} at cost {00.00}.", requestId, routeIndex, insertionCost);
							}
							costCalc.destroy();
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
			if (costCalc.isFeasible()) { // not always feasible because we might miss own time window etc
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

	protected RouteRequest getBestPartialInsertion(Solution s, int requestId, RouteRequestType type) {
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
									bestTransfer = t;
									Logger.debug("Found a new best partial insertion: request {000} into route with index {} at cost {00.00}.", requestId, routeIndex, insertionCost);
								}
								costCalc = s.copy();
							}
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
				bestRoute = tempRoute.copy();
				bestTransfer = t;
				bestRouteIndex = -1;
				bestInsertionCost = cost;
				Logger.debug("Current best: add partial request {000} ({}) as new Route (cost: {00.00}).", sr.id, type, cost);
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
	
	public Node openBestTransfer(Solution s, List<Integer> requestIdsToRepair, int topN){
		//Logger.info("Finding the best transfer facility to open.. there are {} closed and {} open of the {} in total ", s.closedTransfers.size(), s.openTransfers.size(), s.closedTransfers.size()+s.openTransfers.size());
		LinkedList<Node> transfers = new LinkedList<>();
		LinkedList<Double> distances = new LinkedList<>();
		
		for (Node transfer : s.closedTransfers) {
			double dist = 0;
			for (int idToRepair : requestIdsToRepair) {
				SolutionRequest r = s.requests.get(idToRepair - 1);
				dist += problem.distanceBetween(transfer, r.associatedRequest.pickupNode) + problem.distanceBetween(transfer, r.associatedRequest.dropoffNode);
			}
			dist = (double)transfer.f/dist;
			int insert = Arrays.binarySearch(distances.toArray(), dist);
			if (insert < 0) {
				insert = -insert - 1;
			}
			transfers.add(insert, transfer);
			distances.add(insert, dist);
		}
		
		double y = r.nextDouble();
		double yp = Math.pow(y, 9);
		int index = (int) Math.floor(yp * transfers.size());
		Node transferToOpen = transfers.get(index);
		s.closedTransfers.remove(transferToOpen);
		s.openTransfers.add(transferToOpen);
		return transferToOpen;
	}
	
	protected void removeUselessTransfers(Solution s) {
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
	
	// inner data class
	protected class RouteRequest {

		public RouteRequestType type;
		public Route route;
		public int requestId;
		public int routeIndex;
		public Node transfer;
		public double insertionCost;

		public RouteRequest(Route route, int requestId, int routeIndex, RouteRequestType type) {
			this.route = route;
			this.requestId = requestId;
			this.routeIndex = routeIndex;
			this.type = type;
		}
	}
	
	public enum RouteRequestType {
		PICKUP_AND_TRANSFER, TRANSFER_AND_DROPOFF, NO_TRANSFER
	}

}
