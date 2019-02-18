package data;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class Problem {

	public int index;
	public int numRequests;
	public int numTransferCandidates;
	public int numDepots;
	public int capacity;
	public int travelCost; // per unit distance
	
	public double[][] distanceMatrix;
	public double[][] costMatrix;
	
	public ArrayList<Request> requests = new ArrayList<>();
	public ArrayList<TransferNode> transfers = new ArrayList<>();
	public ArrayList<DepotNode> depots = new ArrayList<>();
		
	public void preCalcDistances() {
		// create a list that holds all, then sort them
		ArrayList<Node> all = new ArrayList<>();
		for (Request r : requests) {
			all.addAll(Arrays.asList(r.getNodes()));
		}
		all.addAll(transfers);
		all.addAll(depots);
		all.sort((left, right) -> left.id - right.id);
		
		distanceMatrix = new double[all.size()][all.size()];
		costMatrix = new double[all.size()][all.size()];
		for (Node a : all) {
			for (Node b : all) {
				if (a == b) {
					distanceMatrix[a.id-1][b.id-1] = 0; // ids are 1-indexed, arrays 0-indexed
				} else {
					distanceMatrix[a.id-1][b.id-1] = Math.sqrt((a.x-b.x) * (a.x-b.x) + (a.y-b.y) * (a.y-b.y)); // Euclidian distance
				}
				costMatrix[a.id-1][b.id-1] = travelCost * distanceMatrix[a.id-1][b.id-1];
			}
		}
		for (int i = 0; i < distanceMatrix.length; i++) {
			for (int j = 0; j < distanceMatrix[i].length; j++) {
				System.out.printf("%5.2f ", distanceMatrix[i][j]);
			}
			System.out.println();
		}
	}
	
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
