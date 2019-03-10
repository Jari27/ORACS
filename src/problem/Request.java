package problem;

public class Request {

	public int id; // this id is equal to the pickupnode id
	public int L;
	public Node pickupNode;
	public Node dropoffNode;

	public Request(int pickupNodeId, int dropoffNodeId) {
		this.id = pickupNodeId;
		this.pickupNode = new Node(pickupNodeId, NodeType.PICKUP);
		this.dropoffNode = new Node(dropoffNodeId, NodeType.DROPOFF);
	}

	public Node[] getNodes() {
		Node[] res = { pickupNode, dropoffNode };
		return res;
	}

}
