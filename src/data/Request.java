package data;

public class Request {
	
	public int id;
	public int L;
	public PickupNode pickupNode;
	public DropoffNode dropoffNode;
	
	public Request(int id) {
		this.id = id;
		pickupNode = new PickupNode(id);
		dropoffNode = new DropoffNode(id);
	}
	
}
