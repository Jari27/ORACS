/**
 * 
 */
package main;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import org.pmw.tinylog.Logger;

import problem.Problem;
import solution.Route;
import solution.RouteNode;
import solution.RouteNodeType;
import solution.Solution;
import solution.SolutionRequest;

/**
 * @author Jari Meevis
 *
 */
public class ALNS implements Runnable {
	
	Problem p;
	List<Solution> solutions = new ArrayList<>();
	
	Random rand = new Random();
	
	Solution currentSol;
	
	public ALNS(Problem p) {
		this.p = p;
		this.currentSol = new Solution(p);
		this.currentSol.createInitialSolution();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		SolutionRequest destroyed;
//		currentSol.logSolution();
//		Logger.debug("===========");
//		destroyed = destroyRandomRequest(currentSol);
//		Logger.debug("Destroyed request {000}", destroyed.associatedRequest.id);
//		currentSol.logSolution();
//		Logger.debug("Trying to insert request {000}", destroyed.associatedRequest.id);
//		insertRequest(destroyed, currentSol);
		
		for (int i = 0; i < currentSol.requests.size(); i++) {
			Solution copy = currentSol.copy();
			destroyed = destroyRequest(copy, i);
			Logger.debug("Destroyed request {000}", destroyed.associatedRequest.id);
			Logger.debug("Trying to insert request {000}", destroyed.associatedRequest.id);
			insertRequest(destroyed, copy);
			Logger.debug("=============");
		}
		// TODO Auto-generated method stub

	}
	
	// TODO the SolutionRequest does not point to the right nodes
	// nor do the RouteNodes inside the Route
	public void insertRequest(SolutionRequest toInsert, Solution s) {
		// we do this manually now, we will use cheaper feasibility checking later
		
		// try insertion (note: creating a new route is not possible atm TODO)
		Route bestRoute = null;
		double priceOfBestRoute = 0;
		for (Route oldR : s.routes) {
			for (int i = 1; i < oldR.size(); i++) {
				Logger.debug("Inserting pickup of request {000} in route {000} at location {000}", toInsert.associatedRequest.id, oldR.vehicleId, i);
				// first insert pickup
				Route onlyPickupInsert = oldR.copy(); // this also forces the cost to be recalculated
				RouteNode pickup = new RouteNode(toInsert.associatedRequest.pickupNode, RouteNodeType.PICKUP, toInsert.associatedRequest, onlyPickupInsert.vehicleId);
				//toInsert.pickup = pickup;
				
				if (i == 1) { //first place so remove/replace starting depot too
					Logger.debug("Adjusting starting depot since it might be changed due to the above insertion");
					RouteNode depotStart = new RouteNode(pickup.getAssociatedNode().getNearestDepot(), RouteNodeType.DEPOT_START, onlyPickupInsert.vehicleId);
					onlyPickupInsert.remove(0); // remove depot
					onlyPickupInsert.addFirst(pickup); // add pickup
					pickup.setArrival(pickup.getAssociatedNode().getE()); // set arrival time
					pickup.setStartOfS(pickup.getAssociatedNode().getE()); // set starting time
					pickup.setNumPas(1); // set num pass (recall its the first node)
					
					onlyPickupInsert.addFirst(depotStart); // add nearest depot
					depotStart.setDeparture(pickup.getArrival() - p.distanceBetween(pickup.getAssociatedNode(), depotStart.getAssociatedNode())); // set its departure time
				} else { // just insert (no need for change in dropoff depot, because that depends on the dropoff location
					onlyPickupInsert.add(i, pickup);
					RouteNode prevNode = onlyPickupInsert.get(i-1);
					pickup.setArrival(prevNode.getDeparture() + p.distanceBetween(prevNode.getAssociatedNode(), pickup.getAssociatedNode())); // set arrival
					pickup.setStartOfS(Math.max(pickup.getArrival(), pickup.getAssociatedNode().getE()), false); // max of arrival and time window (dont check errors, we do that manually)
					pickup.setNumPas(prevNode.getNumPas() + 1);
					
					// quick check if this insertion is possible
					if (pickup.getStartOfS() > pickup.getAssociatedNode().getL()) { // infeasible insertion, we cannot insert here or later so break
						Logger.debug("Insertion was infeasible due to time window {00.00} <= {00.00} <= {00.00}", pickup.getAssociatedNode().getE(), pickup.getStartOfS(), pickup.getAssociatedNode().getL());
						break;
					} else if (pickup.getNumPas() > p.capacity) { // infeasible selection, over capacity. try next insert place
						Logger.debug("Insertion was infeasible due to capacity {} > {} = max cap", pickup.getNumPas(), p.capacity);
						continue;						
					}
				}
				Logger.debug("Pickup insertion succesful at location {000}", i);
				// we have now inserted a new pickup (it could still be infeasible, depending on L)
				for (int j = i + 1; j < onlyPickupInsert.size(); j++) { // insertion at last spot and second last is same, so we dont need to check last
					Logger.debug("Inserting dropoff of request {000} in route {000} at location {000}", toInsert.associatedRequest.id, oldR.vehicleId, j);
					
					Route insertBoth = onlyPickupInsert.copy();
					RouteNode dropoff = new RouteNode(toInsert.associatedRequest.dropoffNode, RouteNodeType.DROPOFF, toInsert.associatedRequest, onlyPickupInsert.vehicleId);					
					//toInsert.dropoff = dropoff;
					
					// do insertion at location j
					insertBoth.add(j, dropoff);
					// calculate timings
					RouteNode prevNode = insertBoth.get(j - 1);
					dropoff.setArrival(prevNode.getDeparture() + p.distanceBetween(prevNode.getAssociatedNode(), dropoff.getAssociatedNode())); // set arrival time
					dropoff.setStartOfS(Math.max(dropoff.getArrival(), dropoff.getAssociatedNode().getE()), false); // set starting time as max of arrival, e (don't check errors)
					dropoff.setNumPas(prevNode.getNumPas() - 1);
					
					if (dropoff.getStartOfS() > dropoff.getAssociatedNode().getL()) {  // infeasible insertion, we cannot insert here or later so break
						Logger.debug("Insertion was infeasible due to time window {00.00} <= {00.00} <= {00.00}", dropoff.getAssociatedNode().getE(), dropoff.getStartOfS(), dropoff.getAssociatedNode().getL());
						break;
					}
					
					if (j == onlyPickupInsert.size() - 1) { // change ending depot too
						
						RouteNode depotEnd = new RouteNode(dropoff.getAssociatedNode().getNearestDepot(), RouteNodeType.DEPOT_END, onlyPickupInsert.vehicleId);
						insertBoth.removeLast(); // remove depot
						
						insertBoth.addLast(depotEnd); // add nearest depot
						depotEnd.setArrival(dropoff.getDeparture() - p.distanceBetween(dropoff.getAssociatedNode(), depotEnd.getAssociatedNode())); // set its arrival time
					}
					Logger.debug("Dropoff insertion succesful at location {000}", j);
					Logger.debug("Checking feasibility of full route", j);
					// here we have inserted both a pickup and a dropoff
					// let's verify it's feasible using ride time and time window and capacity
					boolean feasible = true;
					
					// verify all nodes
					for (int k = 1; k < insertBoth.size() && feasible; k++) {
						RouteNode cur = insertBoth.get(k);
						RouteNode prev = insertBoth.get(k-1);
						
						// update all timings
						cur.setArrival(prev.getDeparture() + p.distanceBetween(prev.getAssociatedNode(), cur.getAssociatedNode()));
						cur.setStartOfS(Math.max(cur.getArrival(), cur.getAssociatedNode().getE()), false); // dont report on errors, we check those manually
						
						// these nodes havent changed in feasibility (they're before the insertion or a depot)
						// TODO (maybe) it's probably possible to avoid checking nodes that havent changed
						if (k < j || k >= insertBoth.size() - 1) {
							continue;
						}
						
						Logger.debug("Checking feasibility of RouteNode {000}: {}", k, cur.toString());
						
						// verify time window
						if (cur.getStartOfS() > cur.getAssociatedNode().getL()) { // infeasible
							Logger.debug("Route is infeasible due to Node {000} time window {00.00} <= {00.00} <= {00.00}", k, cur.getAssociatedNode().getE(), cur.getStartOfS(), cur.getAssociatedNode().getL());
							feasible = false;
							break;
						} 
						
						// update numpas
						int delta = 0;
						if (cur.getType() == RouteNodeType.DROPOFF || cur.getType() == RouteNodeType.TRANSFER_DROPOFF) {
							delta = -1;
						} else if (cur.getType() == RouteNodeType.PICKUP || cur.getType() == RouteNodeType.TRANSFER_PICKUP) {
							delta = 1;
						}
						cur.setNumPas(prev.getNumPas() + delta);
						
						// verify capacity
						if (cur.getNumPas() > p.capacity) { //infeasible
							Logger.debug("Route is infeasible due to Node {000} capacity {} > {} = max cap", k, cur.getNumPas(), p.capacity);
							feasible = false;
							break;
						}
						Logger.debug("RouteNode {000} is feasible", k);
						
					}
					// verify max ride times of all requests
					// first check max ride time of the insert
					if (dropoff.getStartOfS() - (pickup.getStartOfS() + pickup.getAssociatedNode().s) > toInsert.associatedRequest.L) { 
						Logger.debug("Request {000} is infeasible due max ride time {00.00} > {00.00} = L", toInsert.associatedRequest.id, dropoff.getStartOfS() - (pickup.getStartOfS() + pickup.getAssociatedNode().s), toInsert.associatedRequest.L);
						feasible = false;
					}
					// then all others
					if (feasible) {
						for (SolutionRequest sr : currentSol.requests) {
							if (sr.dropoff.getStartOfS() - (sr.pickup.getStartOfS() + sr.pickup.getAssociatedNode().s) > sr.associatedRequest.L) {
								Logger.debug("Request {000} is infeasible due max ride time {00.00} > {00.00} = L", sr.associatedRequest.id, sr.dropoff.getStartOfS() - (sr.pickup.getStartOfS() + sr.pickup.getAssociatedNode().s), sr.associatedRequest.L);
								feasible = false;
								break;
							}
						} 
					}
					
					if (feasible) {
						double tempCost = insertBoth.getCost(p);
						if (tempCost < priceOfBestRoute || bestRoute == null) {
							priceOfBestRoute = tempCost;
							bestRoute = insertBoth;
							Logger.info("Found a best route with cost = {00.00}", tempCost);
							//bestRoute.log();
						}
					}
				}
			}
		}
		// we found the cheapest route
		// if bestRoute != null ...
		//s.requests.add(toInsert); 
		s.insertRoute(bestRoute, toInsert);
	}
	
	// TODO update slack
	private void removeTwoNodesFromRoute(Route r, RouteNode removal1, RouteNode removal2) {
		int numRemoved = 0;
		RouteNode prev = r.get(0); // points to the last non-removed node
		for (ListIterator<RouteNode> l = r.listIterator(1); l.hasNext();) {
			RouteNode cur = l.next();

			// if we have to remove the current node, remove it
			if (cur == removal1 || cur == removal2) {
				Logger.debug("Removed RouteNode {} from Route {000}, Request {000}", cur.toString(), r.vehicleId, cur.getAssociatedRequest().id);
				l.remove();
				numRemoved++;
			} else {
				// if we have removed exactly one node, we have removed transferpickup
				// so all subsequent nodes have one less passenger
				// however, if we have removed two nodes, the dropoff is removed too
				// so the net difference (+1 -1) is zero, so no more updating
				if (numRemoved == 1) {
					cur.setNumPas(cur.getNumPas() - 1);
				}
				// if we didn't remove the current node but we have already removed some
				// we need to update the arrival time of the current node, based on the last non-removed node
				if (numRemoved > 0) {
					cur.setArrival(prev.getDeparture() + p.distanceBetween(cur.getAssociatedNode(), prev.getAssociatedNode()));
				}
				// finally, update the reference to the previous node if we didn't delete it
				prev = cur;
			}
		}
	}
	
	public SolutionRequest destroyRandomRequest(Solution currentSolution) {
		int index = rand.nextInt(currentSolution.requests.size());
		return destroyRequest(currentSolution, index);
	}
	
	// TODO after removal of routenodes, re-calculate slack and arrival times of route
	public SolutionRequest destroyRequest(Solution currentSolution, int index) {
		if (index > currentSolution.requests.size()) {
			Logger.warn("Trying to remove request {000} but there are only {000} requests! Using random", index, currentSolution.requests.size());
			index = rand.nextInt(currentSolution.requests.size());
		} 
		
		SolutionRequest toRemove = currentSolution.requests.get(index);
		
		if (toRemove.hasTransfer()) {
			int firstVehicle = toRemove.pickup.getVehicleId();
			int secondVehicle = toRemove.dropoff.getVehicleId();
			
			for (ListIterator<Route> lr = currentSolution.routes.listIterator(); lr.hasNext(); ) { 
				Route r = lr.next();
				// First vehicle visits Pickup and transferDropoff
				if (r.vehicleId == firstVehicle) {
					if (r.size() == 4) {
						// this route only does this one request so we can remove the entire route
						r.clear();
						lr.remove();
					} else {
						removeTwoNodesFromRoute(r, toRemove.pickup, toRemove.transferDropoff);
					}
					toRemove.pickup = null;
					toRemove.transferDropoff = null;
					// TODO handle removal from transferlist
					
				}
				// Second vehicle visits transferPickup and dropoff
				if (r.vehicleId == secondVehicle) {
					if (r.size() == 4) {
						// this route only does this one request so we can remove the entire route
						r.clear();
						lr.remove();
					} else {
						removeTwoNodesFromRoute(r, toRemove.dropoff, toRemove.transferPickup);
					}
					toRemove.dropoff = null;
					toRemove.transferPickup = null;
					// TODO handle removal from transferlist
				}
			}
		} else {
			int vehicle = toRemove.pickup.getVehicleId();
			for (ListIterator<Route> lr = currentSolution.routes.listIterator(); lr.hasNext(); ) { 
				Route r = lr.next();
				// no transfer, so the same vehicle visits both transfer and dropoff
				if (r.vehicleId == vehicle) {
					if (r.size() == 4) {
						// this route only does this one request so we can remove the entire route
						r.clear();
						lr.remove();
					} else {
						removeTwoNodesFromRoute(r, toRemove.pickup, toRemove.dropoff);
					}
					toRemove.pickup = null;
					toRemove.dropoff = null;
					// TODO handle removal from transferlist
				}
			}
		}
		return toRemove;
	}
}
