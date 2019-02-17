package data;
import java.util.ArrayList;

public class Problem {

	public int index;
	public int numRequests;
	public int numTransferCandidates;
	public int numDepots;
	public int capacity;
	public int travelCost; // per unit distance
	
	public int[][] distanceMatrix;
	
	public ArrayList<Request> requests = new ArrayList<>();
	public ArrayList<TransferNode> transfers = new ArrayList<>();
	public ArrayList<DepotNode> depots = new ArrayList<>();
	
	// TODO make this one big list?
		
	public void preCalcDistances() {
		
	}
	
	
}
