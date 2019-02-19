package solution;

import problem.Node;

public class RouteNode {
	
		Node associatedNode;

		double waiting; // time you wait at a node before starting service 	= startOfS - arrival
		double slack;	// time you can start service later 				= l - startOfS
		
		double startOfS;
		double arrival;
		double departure;
		
		int numPas;
		
		public RouteNode(Node associatedNode) {
			this.associatedNode = associatedNode;
		}
		
		public void setWaiting(double waiting) {
			this.waiting = waiting;
			this.startOfS = this.arrival + this.waiting;
			this.departure = this.startOfS + this.associatedNode.s;
			this.slack = this.associatedNode.getE() - this.startOfS;
			
			// check feasibility here? Or keep it separate?
			// return true/false if feasible?
			// we might want to allow infeasible stuff, since we need to move a lot of nodes simultaneously in a route to ensure we make our windows
		}
	
}
