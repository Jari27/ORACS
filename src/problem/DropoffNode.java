package problem;

public class DropoffNode extends Node {

	public int requestId;

	public double e, l;

	public DropoffNode(int nodeId, int requestId) {
		super(nodeId);
		this.requestId = requestId;
	}

	@Override
	public boolean hasTimeWindow() {
		return true;
	}

	@Override
	public double getE() {
		return e;
	}

	@Override
	public double getL() {
		return l;
	}

}
