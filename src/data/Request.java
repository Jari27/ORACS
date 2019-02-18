package data;

public class Request {
	
	public int id; // this id is equal to the pickupnode id
	public int L;
	public PickupNode pickupNode;
	public DropoffNode dropoffNode;
	
	public Request(int pickupNodeId, int dropoffNodeId) {
		this.id = pickupNodeId;
		this.pickupNode = new PickupNode(pickupNodeId, id);
		this.dropoffNode = new DropoffNode(dropoffNodeId, id);
	}
	
	public Node[] getNodes(){
		Node[] res = {pickupNode, dropoffNode};
		return(res);
	}
	
}
