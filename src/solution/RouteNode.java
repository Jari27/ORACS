package solution;

import org.pmw.tinylog.Logger;

import problem.Node;

public class RouteNode {

	private Node associatedNode;

	private RouteNodeType type = RouteNodeType.DEFAULT;

	private double waiting = -1; // time you wait at a node before starting service = startOfS - arrival
	private double slack = -1; // time you can start service later = l - startOfS

	private double startOfS = -1;
	private double arrival = -1;
	private double departure = -1;

	private int numPas;

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
		// we might want to allow infeasible stuff, since we need to move a lot of nodes
		// simultaneously in a route to ensure we make our windows
	}

	public void setStartOfS(double startOfS, boolean warnOnError) {
		// is this check necessary?
		if (warnOnError && this.associatedNode.hasTimeWindow()
				&& (startOfS < this.associatedNode.getE() || startOfS > this.associatedNode.getL())) {
			Logger.warn("Invalid starting time for {}", this.toString());
		}
		this.startOfS = startOfS;
		this.waiting = this.startOfS - this.arrival;
		this.departure = this.startOfS + this.associatedNode.s;
		this.slack = this.associatedNode.getL() - this.startOfS;
	}
	
	public void setStartOfS(double startOfS) {
		this.setStartOfS(startOfS, true);
	}

	public void setArrival(double arrival) {
		// keep startOfS constant
		this.arrival = arrival;
		this.waiting = this.arrival - this.startOfS;
	}
	
	public void setDeparture(double departure) {
		this.departure = departure;
	}
	
	public void setNumPas(int numPas) {
		this.numPas = numPas;
	}

	public Node getAssociatedNode() {
		return associatedNode;
	}

	public RouteNodeType getType() {
		return type;
	}

	public double getWaiting() {
		if (this.type == RouteNodeType.DEPOT_END || this.type == RouteNodeType.DEPOT_START) {
			Logger.warn("Retrieving waiting time of depot {000}.", this.associatedNode.id);
		}
		return waiting;
	}

	public double getSlack() {
		return slack;
	}

	public double getStartOfS() {
		if (this.type == RouteNodeType.DEPOT_END || this.type == RouteNodeType.DEPOT_START) {
			Logger.warn("Retrieving start of service time of depot {000}.", this.associatedNode.id);
		}
		return startOfS;
	}

	public double getArrival() {
		if (this.type == RouteNodeType.DEPOT_START) {
			Logger.warn("Retrieving arrival time of (starting) depot {000}.", this.associatedNode.id);
		}
		return arrival;
	}

	public double getDeparture() {
		if (this.type == RouteNodeType.DEPOT_START) {
			Logger.warn("Retrieving departure time of (ending) depot {000}.", this.associatedNode.id);
		}
		return departure;
	}

	public int getNumPas() {
		if (this.type == RouteNodeType.DEPOT_END || this.type == RouteNodeType.DEPOT_START) {
			Logger.warn("Retrieving number of passengers of depot {000}.", this.associatedNode.id);
		}
		return numPas;
	}
	
	@Override
	public String toString() {
		// TODO
		return String.format(
				"RouteNode associated with node %03d; type = %s, arrival = %0.2f, start of service = %0.2f",
				this.associatedNode.id, this.type, this.arrival, this.startOfS);
	}
	
	public RouteNode copy() {
		RouteNode rnNew = new RouteNode(this.associatedNode, this.type);
		
		// Set all fields -> TODO ensure we update this as we update RouteNode
		rnNew.arrival = this.arrival;
		rnNew.setStartOfS(this.startOfS, true); // also sets slack, waiting
		rnNew.departure = this.departure; // set manually since some DepotNodes have no arrival/startOfS
		rnNew.numPas = this.numPas;
		
		return rnNew;
	}
}
