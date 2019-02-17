import java.util.ArrayList;

public class Problem {

	int index;
	int numRequests;
	int numTransferCandidates;
	int numDepots;
	int capacity;
	int travelCost; // per unit distance
	
	int[][] distanceMatrix;
	
	ArrayList<Request> requests = new ArrayList<>();
	ArrayList<Transfer> transfers = new ArrayList<>();
	ArrayList<Depot> depots = new ArrayList<>();
		
	public void preCalcDistances() {
		
	}
	
	
}
