package main;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import problem.DepotNode;
import problem.Problem;
import problem.Request;
import problem.TransferNode;
import solution.Solution;

import org.pmw.tinylog.Logger;

/**
 * @author Jari Meevis
 *
 */
public class Main {
	
	final static String FILE_NAME = "example_instances.csv";
	static List<Problem> problems;
	static List<Solution> solutions;

	public static void main(String[] args) throws IOException {
		Logger.info("Starting main program.");
		problems = createProblemInstances();
		solutions = new ArrayList<>();
		for (Problem p : problems) {
			Solution s = new Solution(p);
			s.createInitialSolution();
			solutions.add(s);
		}
		
		// quick and dirty removal check
		ALNS test = new ALNS(problems.get(5));
		test.run();
		
		if (true) return;
		
		// Quick and dirty copying check
		
		@SuppressWarnings("unused")
		Solution sol = solutions.get(0);
		Solution copy = sol.copy();
		
		sol.logSolution();
		Logger.debug("-----------");
		copy.logSolution();
		
		Logger.debug("=============");
		Logger.debug("=============");
		
		copy.routes.get(1).get(1).setStartOfS(copy.routes.get(1).get(1).getStartOfS() + 10); // might warn for infeasibility
		
		sol.logSolution();
		Logger.debug("-----------");
		copy.logSolution();
		
		Logger.debug("=============");
		Logger.debug("=============");
		
		Solution copy2 = copy.copy();
		copy2.logSolution();
		
//		Solution s = new Solution(problems.get(0));

	}
	
	/**
	 * Creates a list of problem instances as parsed from the CSV.
	 * 
	 * @return List<Problem> a list of problems as parsed from the CSV.
	 * @throws IOException
	 */
	static List<Problem> createProblemInstances() throws IOException {
		ArrayList<Problem> problems = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(FILE_NAME))) {
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
	
	/**
	 * Parses a single problem instance. All numbers should be integers. There are no checks on feasibility or correctness during the parsing.
	 * 
	 * @param data a (trimmed) integer array representing a problem instance. Should follow the data description
	 * @return a Problem
	 */
	static Problem parseInstance(int[] data) {
		Problem p = new Problem();
		p.index = data[0];
		Logger.info("Parsing problem instance {000}", p.index);
		p.numRequests = data[1];
		p.numTransferCandidates = data[2];
		p.numDepots = data[3];
		p.capacity = data[4];
		p.travelCost = data[5];
		// create request data
		int requestWindowOffset = 6 + 3 * p.numTransferCandidates + 4 * p.numRequests + 2 * p.numDepots;
		int requestServiceTimeOffset = 6 + 3 * p.numTransferCandidates + 2* p.numDepots + 9 * p.numRequests;
		int requestMaxRideTimeOffset = requestWindowOffset + 4 * p.numRequests;
		int requestLocationOffset = 6 + p.numTransferCandidates;
		for (int i = 0; i < p.numRequests; i++) {
			int pickupNodeId = i + 1;
			int dropoffNodeId = i + 1 + p.numRequests;
			Request r = new Request(pickupNodeId, dropoffNodeId);
			r.pickupNode.e = data[2*i + requestWindowOffset];
			r.pickupNode.l = data[2*i + 1 + requestWindowOffset];
			r.dropoffNode.e = data[2*i + requestWindowOffset + 2 * p.numRequests];
			r.dropoffNode.l = data[2*i + 1 + requestWindowOffset + 2 * p.numRequests];
			r.pickupNode.x = data[2*i + requestLocationOffset];
			r.pickupNode.y = data[2*i + 1 + requestLocationOffset];
			r.dropoffNode.x = data[2*i + requestLocationOffset + 2 * p.numRequests];
			r.dropoffNode.y = data[2*i + 1 + requestLocationOffset + 2 * p.numRequests];
			r.L = data[i + requestMaxRideTimeOffset];
			r.pickupNode.s = data[i + requestServiceTimeOffset];
			r.dropoffNode.s = data[i + requestServiceTimeOffset + p.numRequests];
			if (r.pickupNode.l < r.pickupNode.e || r.dropoffNode.l < r.dropoffNode.e || r.dropoffNode.l < r.pickupNode.e || r.pickupNode.s < 0 || r.dropoffNode.s < 0) {
				Logger.warn("Impossible pickup/dropoff windows. We can never make this/something is wrong with the parser/something is wrong with the problem.");
			}
			p.requests.add(r);
			//System.out.printf("Request  %03d: L = %d\n", r.id, r.L);
			Logger.debug("Pickup \t{000}:\t({00}, {00})\ts = {00}\ttime = [{00.00}, {00.00}]\tL={  0}",r.pickupNode.id, r.pickupNode.x, r.pickupNode.y, r.pickupNode.s, r.pickupNode.e, r.pickupNode.l, r.L);
			Logger.debug("Dropoff\t{000}:\t({00}, {00})\ts = {00}\ttime = [{00.00}, {00.00}]",r.dropoffNode.id, r.dropoffNode.x, r.dropoffNode.y, r.dropoffNode.s, r.dropoffNode.e, r.dropoffNode.l);
		}
		// create transfer node date
		int transferServiceTimeOffset = 6 + 11 * p.numRequests + 2 * p.numDepots + 3 * p.numTransferCandidates;
		int transferLocationOffset = 6 + p.numTransferCandidates + 4 * p.numRequests;
		for (int i = 0; i < p.numTransferCandidates; i++) {
			TransferNode t = new TransferNode(1 + p.numRequests * 2 + i);
			t.f = data[6 + i];
			t.s = data[i + transferServiceTimeOffset];
			t.x = data[2*i + transferLocationOffset];
			t.y = data[2*i + 1 + transferLocationOffset];
			p.transfers.add(t);
			Logger.debug("Transf.\t{000}:\t({00}, {00})\ts = {00}\tf = {00}", t.id, t.x, t.y, t.s, t.f);
		}
		// create depot data
		int depotLocationOffset = 6 + p.numTransferCandidates + 4 * p.numRequests + 2 * p.numTransferCandidates;
		for (int i = 0; i < p.numDepots; i++) {
			DepotNode d = new DepotNode(i+1 + p.numRequests * 2 + p.numTransferCandidates);
			d.x = data[2*i + depotLocationOffset];
			d.y = data[2*i + 1 + depotLocationOffset];
			p.depots.add(d);
			Logger.debug("Depot  \t{000}:\t({00}, {00})", d.id, d.x, d.y);
		}
		// preprocess the instance
		p.preProcess();
		return p;
	}

}
