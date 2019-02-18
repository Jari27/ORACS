package problem;

public class PickupNode extends Node {
	
	public int requestId;
	
	public int e, l;
	public int s;
	
	public PickupNode(int nodeId, int requestId) {
		super(nodeId);
		this.requestId = requestId;
	}
	
}
