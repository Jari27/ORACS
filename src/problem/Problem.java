package problem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Problem {

	public int index;
	public int numRequests;
	public int numTransferCandidates;
	public int numDepots;
	public int capacity;
	public int travelCost; // per unit distance
	
	public double[][] distanceMatrix;
	public double[][] costMatrix;
	
	public List<Request> requests = new ArrayList<>();
	public List<TransferNode> transfers = new ArrayList<>();
	public List<DepotNode> depots = new ArrayList<>();
	
	public Map<Node, Node> nearestDepot = new HashMap<>();
		
	
	/* Runs all functions that are necessary for preprocessing the nodes */
	public void preProcess() {
		// create a list that holds all nodes, then sorts them
		List<Node> allNodes = getAllNodes(true);
		
		this.preCalcDistances(allNodes);
		this.calculateNearestDepots(allNodes);
	}
	
	/* Returns a list containing all nodes */
	public List<Node> getAllNodes(boolean sorted) {
		ArrayList<Node> allNodes = new ArrayList<>();
		for (Request r : requests) {
			allNodes.addAll(Arrays.asList(r.getNodes()));
		}
		allNodes.addAll(transfers);
		allNodes.addAll(depots);
		if (sorted) {
			allNodes.sort((left, right) -> left.id - right.id);
		}
		return allNodes;
	}
	
	/* Calculates the nearest depot, its distance and associated costs for each node */
	private void calculateNearestDepots(List<Node> allNodes) {
		for (Node a : allNodes) {
			// we do not skip the depots
			// calculate nearest depot for all other nodes
			DepotNode nearest = null;
			double distance = -1;
			for (DepotNode d : depots) {
				double tmpDist = distanceBetween(a, d);
				if (distance == -1 || tmpDist < distance) {
					nearest = d;
					distance = tmpDist;
				}
			}
			a.setNearestDepot(nearest, travelCost);
		}
	}
	
	/* Calculates an array with distances between nodes, so we don't have to do on the fly calculations */
	private void preCalcDistances(List<Node> allNodes) {
		distanceMatrix = new double[allNodes.size()][allNodes.size()];
		costMatrix = new double[allNodes.size()][allNodes.size()];
		
		// iterate over all node to calculate distances/costs
		for (Node a : allNodes) {
			for (Node b : allNodes) {
				if (a == b) {
					distanceMatrix[a.id-1][b.id-1] = 0; // ids are 1-indexed, arrays 0-indexed
				} else {
					distanceMatrix[a.id-1][b.id-1] = Math.sqrt((a.x-b.x) * (a.x-b.x) + (a.y-b.y) * (a.y-b.y)); // Euclidian distance
				}
				costMatrix[a.id-1][b.id-1] = travelCost * distanceMatrix[a.id-1][b.id-1];
			}
		}
		
		printDistanceMatrix();
	}
	
	/* Prints the distance matrix with node ids */
	private void printDistanceMatrix() {
		System.out.printf("     ");
		for (int i = 0; i < distanceMatrix.length; i++) {
			System.out.printf("   %03d", i + 1);
		}
		System.out.printf("\n");
		for (int i = 0; i < distanceMatrix.length; i++) {
			System.out.printf("%03d   ", i + 1);
			for (int j = 0; j < distanceMatrix[i].length; j++) {
				System.out.printf("%5.2f ", distanceMatrix[i][j]);
			}
			System.out.println();
		}
	}
	
	// find the nearest depot for some node a
	public Node getNearestDepot(Node a) {
		if (a instanceof DepotNode) {
			System.err.printf("%s is already a depot!\n");
		}
		// try to retrieve it before calculating it
		Node nearest = nearestDepot.get(a);
		if (nearest != null) {
			return nearest;
		}
		// we havent seen it yet, so calculate
		double distance = -1;
		for (DepotNode d : depots) {
			double tmpDist = distanceBetween(a, d);
			if (distance == -1 || tmpDist < distance) {
				nearest = d;
				distance = tmpDist;
			}
		}
		// save it for future use
		nearestDepot.put(a, nearest);
		return nearest;
	}
	
	// finds the distance to the nearest depot for some node a
	public double getDistanceToNearestDepot(Node a) {
		return distanceBetween(a, getNearestDepot(a));
	}
	
	/* should probably be a check on this stuff to prevent NPEs, but that will cost computer time */
	public double distanceBetween(Node a, Node b) {
		return distanceBetween(a.id, b.id);
	}
	
	public double distanceBetween(int id1, int id2) {
		return distanceMatrix[id1-1][id2-1];
	}
	
	public double costBetween(Node a, Node b) {
		return costBetween(a.id, b.id);
	}
	
	public double costBetween(int id1, int id2) {
		return costMatrix[id1-1][id2-1];
	}
	
	
}