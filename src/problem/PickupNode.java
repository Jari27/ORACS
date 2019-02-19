package problem;

public class PickupNode extends Node {
	
	public int requestId;
	
	public int e, l;
	
	public PickupNode(int nodeId, int requestId) {
		super(nodeId);
		this.requestId = requestId;
	}
	
	@Override
	public boolean hasTimeWindow() {
		return true;
	}
	
	@Override
	public int getE() {
		return e;
	}
	
	@Override
	public int getL() {
		return l;
	}
	
}
