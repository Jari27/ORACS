package main;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import problem.Node;
import problem.NodeType;
import problem.Problem;
import problem.Request;
import solution.Solution;

import org.pmw.tinylog.Logger;

/**
 * @author Jari Meevis
 *
 */
public class Main {
	
	final static String FILE_NAME = "large_instances.csv";
	static List<Problem> problems;
	static List<Solution> solutions;

	public static void main(String[] args) throws IOException {
		Logger.info("Starting main program.");
//		if (args.length > 0) {
//			Problem p = getSpecificProblem(Integer.parseInt(args[0]));
//			ALNSRunner runner = new ALNSRunner();
//			runner.assignProblem(p);
//			runner.start();
//			return;
//		}
////		for (Problem p : problems) {
////			ALNS test = new ALNS(p);
////			test.run();
////		}
//		problems = createProblemInstances();
//		solutions = new ArrayList<>();
		
//		int problem = 55;
//		ALNS test = new ALNS(problems.get(problem));
//		problems.clear();
//		for (Problem p : problems) {
//			if (p.index != problem + 1) {
//				p = null;
//			}
//		}
//		test.run();

		
//		if (true) return;
		
		
		
		int start = Integer.parseInt(args[0]);
		int end = Integer.parseInt(args[1]);
		int it = Integer.parseInt(args[2]);
		int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), end-start + 1);
//		int iter = Integer.parseInt(args[2]);
		
		ALNSRunner[] runners = new ALNSRunner[numThreads];
		
		for (int i = 0; i < numThreads; i++) {
			int index = i + start;
			runners[i] = new ALNSRunner();
			runners[i].setIterations(it);
			while (index <= end && index <= 195) {
				runners[i].assignProblem(getSpecificProblem(index));
				index += numThreads;
			}
			runners[i].start();
		}
//		problems.clear();
//		problems = null;
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
				String[] stringData = line.split(",");
				int[] intData = new int[stringData.length];
				for (int i = 0; i < stringData.length; i++) {
					intData[i] = Integer.parseInt(stringData[i]);
				}
				problems.add(parseInstance(intData));
				
			}
			br.close();
		}
		return problems;
	}
	
	static Problem getSpecificProblem(int index) throws IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(FILE_NAME))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] stringData = line.split(",");
				int[] intData = new int[stringData.length];
				for (int i = 0; i < stringData.length; i++) {
					intData[i] = Integer.parseInt(stringData[i]);
				}
				if (intData[0] == index) {
					br.close();
					return parseInstance(intData);
				}
				
			}
		}
		return null;
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
			Logger.trace("Pickup \t{000}:\t({00}, {00})\ts = {00}\ttime = [{00.00}, {00.00}]\tL={  0}",r.pickupNode.id, r.pickupNode.x, r.pickupNode.y, r.pickupNode.s, r.pickupNode.e, r.pickupNode.l, r.L);
			Logger.trace("Dropoff\t{000}:\t({00}, {00})\ts = {00}\ttime = [{00.00}, {00.00}]",r.dropoffNode.id, r.dropoffNode.x, r.dropoffNode.y, r.dropoffNode.s, r.dropoffNode.e, r.dropoffNode.l);
		}
		// create transfer node date
		int transferServiceTimeOffset = 6 + 11 * p.numRequests + 2 * p.numDepots + 3 * p.numTransferCandidates;
		int transferLocationOffset = 6 + p.numTransferCandidates + 4 * p.numRequests;
		for (int i = 0; i < p.numTransferCandidates; i++) {
			Node t = new Node(1 + p.numRequests * 2 + i, NodeType.TRANSFER);
			t.f = data[6 + i];
			t.s = data[i + transferServiceTimeOffset];
			t.x = data[2*i + transferLocationOffset];
			t.y = data[2*i + 1 + transferLocationOffset];
			p.transfers.add(t);
			Logger.trace("Transf.\t{000}:\t({00}, {00})\ts = {00}\tf = {00}", t.id, t.x, t.y, t.s, t.f);
		}
		// create depot data
		int depotLocationOffset = 6 + p.numTransferCandidates + 4 * p.numRequests + 2 * p.numTransferCandidates;
		for (int i = 0; i < p.numDepots; i++) {
			Node d = new Node(i+1 + p.numRequests * 2 + p.numTransferCandidates, NodeType.DEPOT);
			d.x = data[2*i + depotLocationOffset];
			d.y = data[2*i + 1 + depotLocationOffset];
			p.depots.add(d);
			Logger.trace("Depot  \t{000}:\t({00}, {00})", d.id, d.x, d.y);
		}
		// preprocess the instance
		p.preProcess();
		return p;
	}

}
