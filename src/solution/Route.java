package solution;

import java.util.LinkedList;
import java.util.ListIterator;
import problem.PickupNode;
import problem.DropoffNode;
import problem.Request;


@SuppressWarnings("serial")
public class Route extends LinkedList<RouteNode>{
	
	public int vehicleId = -1;
	
	public Route(int vehicleId) {
		this.vehicleId = vehicleId;
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
