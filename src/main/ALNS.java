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
		currentSol.logSolution();
		Logger.debug("===========");
		SolutionRequest destroyed = destroyRandomRequest(currentSol);
		Logger.debug("Destroyed request {000}", destroyed.associatedRequest.id);
		currentSol.logSolution();
		// TODO Auto-generated method stub
		

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
	
	// TODO after removal of routenodes, re-calculate slack and arrival times of route
	public SolutionRequest destroyRandomRequest(Solution currentSolution) {
		int index = rand.nextInt(currentSolution.requests.size());
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
