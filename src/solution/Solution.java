package solution;

import java.util.ArrayList;
import java.util.List;

import problem.Problem;
import problem.Request;

public class Solution {
	
	int index;
	
	Problem p;
	
	int numVehicles = 0;
	List<Route> routes = new ArrayList<>();
	
	public Solution(Problem p) {
		this.index = p.index;
		this.p = p;
		this.createInitialSolution();
	}
	
	void createInitialSolution() {
		for (Request r : p.requests) {
			double dist = p.distanceBetween(r.pickupNode, r.dropoffNode);
			if (dist > r.L) {
				System.out.printf("No solution for request %03d, distance between pickup and dropoff too large\n", r.id);
			}
			Route route = new Route();
			
			RouteNode pickup = new RouteNode(r.pickupNode);
			RouteNode dropoff = new RouteNode(r.dropoffNode);
			
			// TODO encode this with getters and setters to automatically update all values
			
			// arrive early and wait
			pickup.arrival = pickup.associatedNode.getE();
			pickup.startOfS = pickup.arrival;
			pickup.slack = pickup.associatedNode.getE() - pickup.startOfS;
			pickup.waiting = pickup.startOfS - pickup.arrival;
			
			dropoff.arrival = pickup.startOfS + pickup.associatedNode.s + p.distanceBetween(pickup.associatedNode, dropoff.associatedNode);
			dropoff.startOfS = dropoff.arrival < dropoff.associatedNode.getE() ? dropoff.associatedNode.getE() : dropoff.arrival;
			dropoff.waiting = dropoff.startOfS - dropoff.arrival;
			dropoff.slack = dropoff.associatedNode.getL() - dropoff.startOfS;
			
			// ensure max ride time is satisfied
			
			// TODO can waiting be negative? What happens?
			// TODO make this automagically calculate the next one; what happens when we have multiple in a row. Just try and it check feasibility afterwards? 
			// TODO how to move longer routes to ensure they still 
			if (dropoff.startOfS - (pickup.startOfS + pickup.associatedNode.s) > r.L) {
				pickup.startOfS = pickup.startOfS + dropoff.waiting > pickup.slack ? pickup.startOfS + pickup.slack : pickup.startOfS + dropoff.waiting;
			}
			
			// TODO test
			
			
			
			
		}
	}
	
}
