package solution;


import java.util.LinkedList;
import java.util.ListIterator;

import org.pmw.tinylog.Logger;

import problem.PickupNode;
import problem.DropoffNode;
import problem.Request;
import problem.Problem;
import solution.SolutionRequest;

@SuppressWarnings("serial")
public class Route extends LinkedList<RouteNode>{
	
	public int vehicleId = -1;
	
	public Route(int vehicleId) {
		this.vehicleId = vehicleId;
	}
	
	// TODO: cache this?
	public double getCost(Problem p) {
		double cost = 0;
		for (int i = 0; i < this.size() - 1; i++) {
			cost += p.costBetween(this.get(i).getAssociatedNode(), this.get(i+1).getAssociatedNode());
		}
		return cost;
	}

	public Route copy() {
		Route r = new Route(this.vehicleId);
		for (RouteNode cur : this) {
			RouteNode next = cur.copy();
			r.add(next);
		}
		return r;
	}
	
	public void isFeasible(){
		Logger.debug("Checking if Route {000} is feasible..", this.vehicleId);
		int index1 = 0;
		int index2 = 0;
		for (ListIterator<RouteNode> l = listIterator(0); l.hasNext();){
			RouteNode cur = l.next();
			index1 += nodeIsFeasible(cur);
			index2 += 1;
		}
		if(index1 == index2){
			Logger.debug("Route {000} is feasible..", this.vehicleId);
			//return true;
		}else{
			Logger.debug("Route {000} is unfeasible..", this.vehicleId);
			//return false;
		}
	}
		
	public int nodeIsFeasible(RouteNode rn) {
		RouteNodeType type = rn.getType();
		switch(type){
		case DEPOT_START:
			Logger.debug("This is the starting depot, time window always satisfied");
			return 1;
		case PICKUP:
			Request associatedRp = rn.getAssociatedRequest();
			PickupNode pickup = associatedRp.getPickup();
			Logger.debug("This is a pickup node with time window: {00} - {00}", pickup.getE(), pickup.getL());
			if(rn.getStartOfS() >= pickup.getE() && rn.getStartOfS() <= pickup.getL()){
				Logger.debug("The service starts at {000} which lies within the time window", rn.getStartOfS());
				return 1;
			}else{
				Logger.debug("The service starts at {000} which does not lie within the time window", rn.getStartOfS());
				return 0;
			}
		case DROPOFF:
			Request associatedRd = rn.getAssociatedRequest();
			DropoffNode dropoff = associatedRd.getDropoff();
			SolutionRequest sR = rn.getSolutionRequest();
			RouteNode assPickup = sR.pickup;
			double startOfSpickup = assPickup.getStartOfS();
			
		Logger.debug("This is a dropoff node with time window: {00} - {00} and max ride time: {000}", dropoff.getE(), dropoff.getL(), associatedRd.L);
			if(rn.getStartOfS() >= dropoff.getE() && rn.getStartOfS() <= dropoff.getL() && rn.getStartOfS() - startOfSpickup < associatedRd.L){
				Logger.debug("Start service dropoff: {000}, start service pickup: {000}, ride time: {000}", rn.getStartOfS(),
						startOfSpickup, rn.getStartOfS() - startOfSpickup);
				return 1;
			}else{
				Logger.debug("Start service dropoff: {000}, start service pickup: {000}, ride time: {000}", rn.getStartOfS(),
						startOfSpickup, rn.getStartOfS() - startOfSpickup);
				return 0;
			}
		case DEPOT_END:
			Logger.debug("This is the ending depot, time window always satisfied");
			return 1;
		case TRANSFER_PICKUP:
			Logger.debug("This is a tranfer pickup node, it has no hard time window");
			return 1;
		case TRANSFER_DROPOFF:
			Logger.debug("This is a tranfer dropoff node, it has no hard time window");
			return 1;
		default:
			Logger.debug("This is a default node");
			return 1;
		}
	}
	// TODO some function to calculate feasible times given a route
	// TODO some function to check whether an insertion is valid?
}
