package solution;

import org.pmw.tinylog.Logger;

import problem.Node;
import problem.Request;

/**
 * A RouteNode defines an 'action'. It is a pickup/dropoff/depot visit at a certain time with an associated request.
 * Each transfer/depot can have multiple RouteNodes associated with it; but each pickup and dropoff (for a request) should
 * only be visited once and thus only have 1 associated RouteNode. Each RouteNode 'knows' what the underlying Node 
 * and Request (if applicable) are. 
 * 
 * 
 * @author jarim
 *
 */
public class RouteNode {

	private Node associatedNode;
	private Request associatedRequest; // only valid if it's not a depot
	private RouteNodeType type = RouteNodeType.DEFAULT;
	private SolutionRequest associatedSolutionRequest;
	
	// The above three fields define a distinct routenode (e.g. type of transfer for request x)
	
	private int vehicleId = -1;
	
	private double waiting = -1; // time you wait at a node before starting service = startOfS - arrival
	private double slack = -1; // time you can start service later = l - startOfS
	
	private double virtualE, virtualL = -1; // TODO add getter, setter, check and function to update

	private double startOfS = -1;
	private double arrival = -1;
	private double departure = -1;

	private int numPas;

	// only for depots
	public RouteNode(Node associatedNode, RouteNodeType type, int vehicleId) {
		if (type != RouteNodeType.DEPOT_END && type != RouteNodeType.DEPOT_START) {
			Logger.warn("Attempting to create non-depot {000} without associated request", associatedNode.id);
		}
		this.numPas = 0;
		this.associatedNode = associatedNode;
		this.type = type;
		this.vehicleId = vehicleId;
	}
	
	// all other nodes
	/**
	 * Creates a non-depot node
	 * 
	 * @param associatedNode the associated node
	 * @param type the type of node
	 * @param associatedRequest the associated request (if not a depot)
	 * @param vehicleId the vehicle this node belongs to
	 */
	public RouteNode(Node associatedNode, RouteNodeType type, Request associatedRequest, int vehicleId) {
		if (type == RouteNodeType.DEPOT_END || type == RouteNodeType.DEPOT_START) {
			Logger.warn("Attempting to create depot {000} with associated request {000}", associatedNode.id, associatedRequest.id);
		}
		this.associatedNode = associatedNode;
		this.type = type;
		this.associatedRequest = associatedRequest;
		this.vehicleId = vehicleId;
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
			Logger.warn("Invalid starting time {00.00} <= {00.00} (= SoS) <= {00.00} for {}", this.associatedNode.getE(), startOfS, this.associatedNode.getL(), this.toString());
		}
		this.startOfS = startOfS;
		this.waiting = this.startOfS - this.arrival;
		this.departure = this.startOfS + this.associatedNode.s;
		this.slack = this.associatedNode.getL() - this.startOfS;
	}
	
	public double getVirtualE() {
		if (this.getType() == RouteNodeType.DEPOT_END || this.getType() == RouteNodeType.DEPOT_START) {
			Logger.warn("Retrieving virtual E of depot. This should not happen.");
		}
		return this.virtualE;
	}
	
	public double getVirtualL() {
		if (this.getType() == RouteNodeType.DEPOT_END || this.getType() == RouteNodeType.DEPOT_START) {
			Logger.warn("Retrieving virtual E of depot. This should not happen.");
		}
		return this.virtualL;
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

	public Request getAssociatedRequest() {
		if (this.type == RouteNodeType.DEPOT_END || this.type == RouteNodeType.DEPOT_START) {
			Logger.warn("Attempting to get associated request for depot {000}", associatedRequest.id, this.associatedNode.id);
		}
		return associatedRequest;
	}

	public void setAssociatedRequest(Request associatedRequest) {
		if (this.type == RouteNodeType.DEPOT_END || this.type == RouteNodeType.DEPOT_START) {
			Logger.warn("Attempting to set associated request {000} for depot {000}", associatedRequest.id, this.associatedNode.id);
		}
		this.associatedRequest = associatedRequest;
	}
	
	public void setSolutionRequest(SolutionRequest solutionRequest){
		if (this.type == RouteNodeType.DEPOT_END || this.type == RouteNodeType.DEPOT_START) {
			Logger.warn("Attempting to set associated request {000} for depot {000}", associatedRequest.id, this.associatedNode.id);
		}
		this.associatedSolutionRequest = solutionRequest;
	}
	
	public SolutionRequest getSolutionRequest() {
		if (this.type == RouteNodeType.DEPOT_END || this.type == RouteNodeType.DEPOT_START) {
			Logger.warn("Attempting to get associated request for depot {000}", this.associatedNode.id);
		}
		return associatedSolutionRequest;
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
		if (this.type == RouteNodeType.DEPOT_END) {
			Logger.warn("Retrieving departure time of (ending) depot {000}.", this.associatedNode.id);
		}
		return departure;
	}

	public int getNumPas() {
//		if (this.type == RouteNodeType.DEPOT_END || this.type == RouteNodeType.DEPOT_START) {
//			Logger.warn("Retrieving number of passengers of depot {000}.", this.associatedNode.id);
//		}
		return numPas;
	}
	
	@Override
	public String toString() {
		return String.format(
				"RouteNode associated with node %03d; type = %s, arrival = %.2f, start of service = %.2f",
				this.associatedNode.id, this.type, this.arrival, this.startOfS);
	}

	public int getVehicleId() {
		return this.vehicleId;
	}
	
	public void setVehicleId(int vehicleId) {
		this.vehicleId = vehicleId;
	}

	public RouteNode copy() {
		RouteNode copy;
		if (this.type == RouteNodeType.DEPOT_END || this.type == RouteNodeType.DEPOT_START) {
			copy = new RouteNode(this.associatedNode, this.type, this.vehicleId);	
		} else {
			copy = new RouteNode(this.associatedNode, this.type, this.associatedRequest, this.vehicleId);
		}
		copy.waiting = this.waiting;
		copy.slack = this.slack;
		copy.startOfS = this.startOfS;
		copy.arrival = this.arrival;
		copy.departure = this.departure;
		copy.numPas = this.numPas;
		return copy;
	}
	
	public boolean isTransfer() {
		return (this.type == RouteNodeType.TRANSFER_DROPOFF || this.type == RouteNodeType.TRANSFER_PICKUP);
	}
	
	public boolean isEqualExceptTimings(RouteNode other) {
		if (other == null) return false;
		if (this == other) return true;
		if (this.vehicleId != other.vehicleId || this.associatedNode != other.associatedNode) return false;
		// if it's a pickup/dropoff, the RouteNode can only have 1 associated request and 1 type, when given the associated Node, so we don't need to compare those
		// if it's a depot, we only care that the vehicleId and associated node is equal
		return (!this.isTransfer() || this.getType() == other.getType() && this.getAssociatedRequest() == other.getAssociatedRequest());
	}
	
}
