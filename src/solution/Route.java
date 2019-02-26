package solution;

import java.util.LinkedList;

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

	// TODO some function to calculate feasible times given a route
	// TODO some function to check whether an insertion is valid?
}
