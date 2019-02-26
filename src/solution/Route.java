package solution;

import java.util.LinkedList;
import java.util.ListIterator;
import problem.PickupNode;
import problem.DropoffNode;
import problem.Request;

import problem.Problem;

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
	
	public boolean isFeasible(){
		int index = 0;
		for (ListIterator<RouteNode> l = listIterator(1); l.hasNext();){
			RouteNode cur = l.next();
			RouteNodeType type = cur.getType();
			index += nodeIsFeasible(cur);
		}
		return true;
	}
		
	public int nodeIsFeasible(RouteNode rn) {
		RouteNodeType type = rn.getType();
		Request associatedR = rn.getAssociatedRequest();
		if(type == RouteNodeType.PICKUP && rn.getStartOfS()>= associatedR.pickupNode.e){
			return 1;
		}else if(type == RouteNodeType.DROPOFF && rn.getStartOfS()>= associatedR.dropoffNode.e){
			return 1;
		}
		return 0;
		
	}
	// TODO some function to calculate feasible times given a route
	// TODO some function to check whether an insertion is valid?
}
