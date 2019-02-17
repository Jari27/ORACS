import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import data.Problem;
import data.Request;
import data.TransferNode;

public class Main {
	
	static String fileName = "example_instances.csv";
	static ArrayList<Problem> problems;

	public static void main(String[] args) throws IOException {
		problems = createProblemInstances();

	}
	
	static ArrayList<Problem> createProblemInstances() throws IOException {
		ArrayList<Problem> problems = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] stringData = line.split(", ");
				int[] intData = new int[stringData.length];
				for (int i = 0; i < stringData.length; i++) {
					intData[i] = Integer.parseInt(stringData[i]);
				}
				problems.add(parseInstance(intData));
				
			}
		}
		return problems;
	}
	
	// TODO locations of nodes, depots
	static Problem parseInstance(int[] data) {
		Problem p = new Problem();
		p.index = data[0];
		System.out.println("Parsing problem instance " + p.index);
		p.numRequests = data[1];
		p.numTransferCandidates = data[2];
		p.numDepots = data[3];
		p.capacity = data[4];
		p.travelCost = data[5];
		// create transfer node date
		int transferServiceTimeOffset = 6 + 11 * p.numRequests + 2 * p.numDepots + 3 * p.numTransferCandidates;
		for (int i = 0; i < p.numTransferCandidates; i++) {
			TransferNode t = new TransferNode();
			t.f = data[6 + i];
			t.s = data[i + transferServiceTimeOffset];
			p.transfers.add(t);
			System.out.printf("Transfer %d: cost = %d, s = %d\n", i+1, t.f, t.s);
		}
		// create request data
		int requestWindowOffset = 6 + 3 * p.numTransferCandidates + 4 * p.numRequests + 2 * p.numDepots;
		int requestServiceTimeOffset = 6 + 3 * p.numTransferCandidates + 2* p.numDepots + 9 * p.numRequests;
		int requestMaxRideTimeOffset = requestWindowOffset + 4 * p.numRequests;
		for (int i = 0; i < p.numRequests; i++) {
			Request r = new Request(i);
			r.pickupNode.e = data[2*i + requestWindowOffset];
			r.pickupNode.l = data[2*i + 1 + requestWindowOffset];
			r.dropoffNode.e = data[2*i + requestWindowOffset + 2 * p.numRequests];
			r.dropoffNode.l = data[2*i + 1 + requestWindowOffset + 2 * p.numRequests];
			r.L = data[i + requestMaxRideTimeOffset];
			r.pickupNode.s = data[i + requestServiceTimeOffset];
			r.dropoffNode.s = data[i + requestServiceTimeOffset + p.numRequests];
			if (r.pickupNode.l < r.pickupNode.e || r.dropoffNode.l < r.dropoffNode.e || r.dropoffNode.l < r.pickupNode.e || r.pickupNode.s < 0 || r.dropoffNode.s < 0) {
				System.out.println("Impossible pickup/dropoff windows. We can never make this/something is wrong with the parser/something is wrong with the problem.");
			}
			p.requests.add(r);
			System.out.printf("Request %d: e_p = %d, l_p = %d, e_d = %d, l_d = %d, s_p = %d, s_d = %d, L = %d\n", i+1, r.pickupNode.e, r.pickupNode.l, r.dropoffNode.e, r.dropoffNode.l, r.pickupNode.s, r.dropoffNode.s, r.L);
		}
		// calculate distance matrix
		p.preCalcDistances();
		return p;
		
	}

}
