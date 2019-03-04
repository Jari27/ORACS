package heuristics.destroy;

import java.util.List;
import java.util.ListIterator;

import org.pmw.tinylog.Logger;

import problem.Problem;
import solution.Route;
import solution.RouteNode;
import solution.Solution;
import solution.SolutionRequest;

public abstract class DestroyHeuristic {
	
	private Problem problem = null;
	
	public DestroyHeuristic(Problem p) {
		this.problem = p;
	}

	public abstract List<SolutionRequest> destroyMultiple(Solution currentSolution, int n);

	public abstract SolutionRequest destroySingle(Solution currentSolution);
	
	
	/**
	 * Destroys a solution request by removing its associated nodes from the routes. It does not remove the SolutionRequest itself from the list of requests.
	 * After this operator, the solution is changed (and incomplete)
	 * 
	 * @param currentSolution the current Solution object in which to destroy the SolutionRequest
	 * @param index the index of the SolutionRequest to destroy
	 * @return a reference to the destroyed SolutionRequest
	 */
	protected SolutionRequest defaultDestroy(Solution currentSolution, int index) {
		if (index > currentSolution.requests.size()) {
			Logger.warn("Trying to remove request {000} but there are only {000} requests! Using last", index,
					currentSolution.requests.size());
			index = currentSolution.requests.size();
		}

		SolutionRequest toRemove = currentSolution.requests.get(index);

		if (toRemove.hasTransfer()) {
			int firstVehicle = toRemove.pickup.getVehicleId();
			int secondVehicle = toRemove.dropoff.getVehicleId();

			for (ListIterator<Route> lr = currentSolution.routes.listIterator(); lr.hasNext();) {
				Route r = lr.next();
				// First vehicle visits Pickup and transferDropoff
				if (r.vehicleId == firstVehicle) {
					if (r.size() == 4) { // no other nodes visited
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
			for (ListIterator<Route> lr = currentSolution.routes.listIterator(); lr.hasNext();) {
				Route r = lr.next();
				if (r.vehicleId == vehicle) {
					if (r.size() == 4) {
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

	// TODO update slack
	protected void removeTwoNodesFromRoute(Route r, RouteNode removal1, RouteNode removal2) {
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
				// if we have removed exactly one node, all subsequent nodes have one passenger less
				// (until we have removed the dropoff too)
				if (numRemoved == 1) {
					cur.setNumPas(cur.getNumPas() - 1);
				}
				// if we did not remove the current node but we have removed some
				// we need to recalculate the arrival time
				if (numRemoved > 0) {
					cur.setArrival(prev.getDeparture() + problem.distanceBetween(cur.getAssociatedNode(), prev.getAssociatedNode()));
				}
				prev = cur;
			}
		}
	}
	
	public abstract String toString();

}
