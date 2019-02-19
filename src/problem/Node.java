package problem;

public class Node {
	public int id;
	public int x, y;
	public int s = 0;
	private DepotNode nearestDepot;
	public double nearestDepotDistance;
	public double nearestDepotCost;
	
	public Node(int id) {
		this.id = id;
	}
	
	/* nearestDepot is private because it allows us to forcibly update the other associated variables when we update the depot*/
	public void setNearestDepot(DepotNode near, double travelCost) {
		if (near == null) {
			System.err.printf("Trying to set nearest depot for node %03d, but the depot is null", id);
		} else {
			this.nearestDepot = near;
			this.nearestDepotDistance = Math.sqrt((this.x - near.x) * (this.x - near.x) + (this.y - near.y) * (this.y - near.y));
			this.nearestDepotCost = this.nearestDepotDistance * travelCost;
		}
	}
	
	public DepotNode getNearestDepot() {
		return this.nearestDepot;
	}
	
	public String toString() {
		return String.format("Node %03d: (%d, %d)", id, x, y);
	}
	
	/* this is really ugly, how can we make this better */
	public boolean hasTimeWindow() {
		return false;
	}
	
	public int getE() {
		return -1;
	}
	
	public int getL() {
		return -1;
	}
	
}