package problem;

public class DropoffNode extends Node {
	
	public int requestId;
	
	public int e, l;
	public int s;
	
	public DropoffNode(int nodeId, int requestId) {
		super(nodeId);
		this.requestId = requestId;
	}
	
}

