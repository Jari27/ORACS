package solution;

import java.util.LinkedList;

@SuppressWarnings("serial")
public class Route extends LinkedList<RouteNode>{
	
	public int vehicleId = -1;
	
	public Route(int vehicleId) {
		this.vehicleId = vehicleId;
	}

	// TODO some function to calculate feasible times given a route
	// TODO some function to check whether an insertion is valid?
}
