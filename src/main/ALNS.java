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
						//this route does more than one request so we just remove the current request
						int numRemoved = 0;
						for (ListIterator<RouteNode> l = r.listIterator(); l.hasNext();) {
							RouteNode rn = l.next();
							if (rn == toRemove.pickup || rn == toRemove.transferDropoff) {
								Logger.debug("Removed RouteNode {} from Route {000}, Request {000}", rn.toString(), r.vehicleId, toRemove.associatedRequest.id);
								l.remove();
								numRemoved++;
							}
						}
						if (numRemoved != 2) {
							Logger.warn("Removed {} instead of 2 from Route {000}, Request {000}", numRemoved, r.vehicleId, toRemove.associatedRequest.id);
						}
						// TODO update route timings and numpassengers
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
					int numRemoved = 0;
						for (ListIterator<RouteNode> l = r.listIterator(); l.hasNext();) {
							RouteNode rn = l.next();
							if (rn == toRemove.dropoff || rn == toRemove.transferPickup) {
								Logger.debug("Removed RouteNode {} from Route {000}, Request {000}", rn.toString(), r.vehicleId, toRemove.associatedRequest.id);
								l.remove();
								numRemoved++;
							}
						}
						if (numRemoved != 2) {
							Logger.warn("Removed {} instead of 2 from Route {000}, Request {000}", numRemoved, r.vehicleId, toRemove.associatedRequest.id);
						}
						// TODO update route timings and numpassengers
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
						int numRemoved = 0;
						for (ListIterator<RouteNode> l = r.listIterator(); l.hasNext();) {
							RouteNode rn = l.next();
							if (rn == toRemove.pickup || rn == toRemove.dropoff) {
								Logger.debug("Removed RouteNode {} from Route {000}, Request {000}", rn.toString(), r.vehicleId, toRemove.associatedRequest.id);
								l.remove();
								numRemoved++;
							}
						}
						if (numRemoved != 2) {
							Logger.warn("Removed {} instead of 2 from Route {000}, Request {000}", numRemoved, r.vehicleId, toRemove.associatedRequest.id);
						}
						// TODO update route timings and numpassengers
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
