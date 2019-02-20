package solution;

import problem.Node;

public class RouteNode {
	
		Node associatedNode;
		
		RouteNodeType type = RouteNodeType.DEFAULT; 

		double waiting = -1; // time you wait at a node before starting service 	= startOfS - arrival
		double slack = -1;	// time you can start service later 				= l - startOfS
		
		double startOfS = -1;
		double arrival = -1;
		double departure = -1;
		
		int numPas;
		
		public RouteNode(Node associatedNode, RouteNodeType type) {
			this.associatedNode = associatedNode;
			this.type = type;
		}
		
		public void setWaiting(double waiting) {
			this.waiting = waiting;
			this.startOfS = this.arrival + this.waiting;
			this.departure = this.startOfS + this.associatedNode.s;
			this.slack = this.associatedNode.getL() - this.startOfS;
			
			// check feasibility here? Or keep it separate?
			// return true/false if feasible?
			// we might want to allow infeasible stuff, since we need to move a lot of nodes simultaneously in a route to ensure we make our windows
		}
		
		public void setStartOfS(double startOfS, boolean report) {
			// is this check necessary?
			if (report && (startOfS < this.associatedNode.getE() || startOfS > this.associatedNode.getL())) {
				System.out.printf("Invalid starting time for RouteNode %s (associated node %03d)\n", this.toString(), this.associatedNode.id);
			}
			this.startOfS = startOfS;
			this.waiting = this.startOfS - this.arrival;
			this.departure = this.startOfS + this.associatedNode.s;
			this.slack = this.associatedNode.getL() - this.startOfS;
		}
		
		public void setArrival(double arrival) {
			// keep startOfS constant
			this.arrival = arrival;
			this.waiting = this.arrival - this.startOfS;
		}
		
		@Override
		public String toString() {
			// TODO
			return String.format("TODO");
		}
	
}
