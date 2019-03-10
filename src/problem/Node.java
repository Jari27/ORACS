package problem;


public class Node {
	public int id;
	public int x, y;
	public double e = 0;
	public double l = Double.POSITIVE_INFINITY;
	public int s = 0;
	public int f = 0;
	public NodeType type;

	public Node(int id, NodeType type) {
		this.id = id;
		this.type = type;
	}

	@Override
	public String toString() {
		return String.format("Node %03d: (%d, %d)", id, x, y);
	}
}