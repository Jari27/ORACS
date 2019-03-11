/**
 * 
 */
package heuristics2.destroy;

import java.util.List;
import java.util.ListIterator;

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
public abstract class DestroyHeuristic {
	
	Problem problem;
	
	public DestroyHeuristic(Problem p) {
		this.problem = p;
	}
	
	/**
	 * Destroys a solution in place
	 * @param s the solution to destroy
	 * @param number the number of request to destroy
	 * @return true if the solution was destroyed successfully, false otherwise
	 */
	public abstract List<Integer> destroy(Solution s, int number);
	
	public boolean destroySpecific(Solution s, int requestId) {
		SolutionRequest sr = s.requests.get(requestId - 1);
		
		if (sr.hasTransfer()) {
			return removeWithTransfer(s, sr);
		} else {
			return removeWithoutTransfer(s, sr);
		}
	}
	
	protected boolean removeWithoutTransfer(Solution s, SolutionRequest sr) {
		int numRemoved = 0;
		for (ListIterator<Route> iter = s.routes.listIterator(); iter.hasNext();) {
			Route route = iter.next();
			if (sr.pickup.vehicleId != route.vehicleId) {
				// wrong route, check next one
				continue;
			}
			if (route.size() == 2) {
				// only this request in route, remove full route
				route.clear();
				iter.remove();
				numRemoved = 2;
			} else {
				// remove the nodes
				RouteNode prev = null;
				for (ListIterator<RouteNode> iter1 = route.listIterator(); iter1.hasNext(); ) {
					RouteNode cur = iter1.next();
					if (cur == sr.pickup || cur == sr.dropoff) {
						iter1.remove();
						numRemoved++;
					} else {
						if (numRemoved > 0) {
							// we need to update the current node
							if (prev == null) {
								cur.setArrival(cur.getAssociatedNode().e);
							} else {
								cur.setArrival(prev.getDeparture() + problem.distanceBetween(cur.getAssociatedNode(), prev.getAssociatedNode()));
							}
						}
						if (numRemoved == 1) { // we've only removed a pickup, so all subs. nodes have one passenger less
							cur.setNumPas(cur.getNumPas() - 1);
						}
					}
				}
				
			}
			sr.pickup = null;
			sr.dropoff = null;
			if (numRemoved == 2) {
				return true;
			} else {
				Logger.warn("Removed {} nodes instead of 2!", numRemoved);
				return false;
			}
		}
		Logger.warn("Couldn't find a route that contains this request!");
		return false;
	}
	
	protected boolean removeWithTransfer(Solution s, SolutionRequest sr) {
		int numRemoved = 0;
		int routesDone = 0;
		for (ListIterator<Route> iter = s.routes.listIterator(); iter.hasNext();) {
			Route route = iter.next();
			if (sr.pickup.vehicleId != route.vehicleId && sr.dropoff.vehicleId != route.vehicleId) {
				// wrong route, check next one
				continue;
			}
			if (route.size() == 2) {
				// only this request in route, remove full route
				route.clear();
				iter.remove();
				numRemoved += 2;
			} else {
				// remove the nodes
				RouteNode prev = null;
				for (ListIterator<RouteNode> iter1 = route.listIterator(); iter1.hasNext(); ) {
					RouteNode cur = iter1.next();
					if (cur == sr.pickup || cur == sr.dropoff || cur == sr.transferDropoff || cur == sr.transferPickup) {
						iter1.remove();
						numRemoved++;
					} else {
						if (numRemoved > 0 && routesDone == 0 || numRemoved > 2 && routesDone == 1) {
							// we need to update the current node
							if (prev == null) {
								cur.setArrival(cur.getAssociatedNode().e);
							} else {
								cur.setArrival(prev.getDeparture() + problem.distanceBetween(cur.getAssociatedNode(), prev.getAssociatedNode()));
							}
						}
						if (numRemoved == 1 || numRemoved == 3) { // we've only removed a pickup, so all subseq. nodes have one passenger less
							cur.setNumPas(cur.getNumPas() - 1);
						}
					}
				}	
			}
			// break if we are finished with removing all
			if (++routesDone == 2) {
				break;
			}
		}
		sr.pickup = null;
		sr.dropoff = null;
		sr.transferDropoff = null;
		sr.transferPickup = null;
		if (numRemoved == 4 && routesDone == 2) {
			return true;
		} else {
			Logger.warn("Removed {}/4 nodes in {}/2 routes!", numRemoved, routesDone);
			return false;
		}
	}

}
