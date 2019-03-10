package solution;

import org.pmw.tinylog.Logger;

import problem.Node;

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

	public Node problemNode;
	public RouteNodeType type;
	public int requestId;
	
	public int vehicleId; // to quickly find the route it belongs to
	
	// The above three fields define a distinct routenode (e.g. type of transfer for request x)

	private double startOfS = -1;
	private double arrival = -1;
	private int numPas = 0;

	/**
	 * Creates a node
	 * 
	 * @param associatedNode the associated node
	 * @param type the type of node
	 * @param requestId the id of the associated request
	 */
	public RouteNode(Node associatedNode, RouteNodeType type, int requestId, int vehicleId) {
		this.problemNode = associatedNode;
		this.type = type;
		this.requestId = requestId;
		this.vehicleId = vehicleId;
	}

	public void setStartOfS(double startOfS, boolean warnOnError) {
		// is this check necessary?
		if ((startOfS < this.problemNode.e || startOfS > this.problemNode.l)) {
			Logger.warn("Invalid starting time {00.00} <= {00.00} (= SoS) <= {00.00} for {}", this.problemNode.e, startOfS, this.problemNode.l, this.toString());
		}
		this.startOfS = startOfS;
	}
	
	public void setStartOfS(double startOfS) {
		this.setStartOfS(startOfS, true);
	}

	public void setArrival(double arrival) {
		// keep startOfS constant
		this.arrival = arrival;
	}
	
	public void setNumPas(int numPas) {
		this.numPas = numPas;
	}

	public Node getAssociatedNode() {
		return problemNode;
	}
	
	public RouteNodeType getType() {
		return type;
	}

	public double getStartOfS() {
		return startOfS;
	}

	public double getArrival() {
		return arrival;
	}

	public double getDeparture() {
		return this.startOfS + this.problemNode.s;
	}

	public int getNumPas() {
		return numPas;
	}
	
	@Override
	public String toString() {
		return String.format(
				"RouteNode associated with node %03d; type = %s, arrival = %.2f, start of service = %.2f",
				this.problemNode.id, this.type, this.arrival, this.startOfS);
	}

	
	/**
	 * Don't forget to manually set the correct associated SolutionRequest
	 * @return a copy of this node
	 */
	public RouteNode copy() {
		//RouteNode copy = new RouteNode(this.associatedNode, this.type, this.associatedRequest, this.vehicleId);
		RouteNode copy = new RouteNode(this.problemNode, this.type, this.requestId, this.vehicleId);
		copy.startOfS = this.startOfS;
		copy.arrival = this.arrival;
		copy.numPas = this.numPas;
		return copy;
	}
	
	public boolean isTransfer() {
		return (this.type == RouteNodeType.TRANSFER_DROPOFF || this.type == RouteNodeType.TRANSFER_PICKUP);
	}
	
	public boolean isEqualExceptTimings(RouteNode other) {
		if (other == null) return false;
		if (this == other) return true;
		if (this.problemNode != other.problemNode) return false;
		// if it's a pickup/dropoff, the RouteNode can only have 1 associated request and 1 type, when given the associated Node, so we don't need to compare those
		// if it's a depot, we only care that the vehicleId and associated node is equal
		return (!this.isTransfer() || this.getType() == other.getType() && this.requestId == other.requestId);
	}
	
}
