package problem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pmw.tinylog.Logger;

public class Problem {

	public int index;
	public int numRequests;
	public int numTransferCandidates;
	public int numDepots;
	public int capacity;
	public int travelCost; // per unit distance

	public double[][] distanceMatrix;
	
	public double maxCost;

	public List<Request> requests = new ArrayList<>();
	public List<Node> transfers = new ArrayList<>();
	public List<Node> depots = new ArrayList<>();

	public Map<Node, Node> nearestDepot = new HashMap<>();

	/* Runs all functions that are necessary for preprocessing the nodes */
	public void preProcess() {
		Logger.info("Preprocessing problem instance {000}", this.index);
		// create a list that holds all nodes, then sorts them
		List<Node> allNodes = getAllNodes(true);

		this.preCalcDistances(allNodes);
		this.calculateNearestDepots(allNodes);
		this.maxCost = this.getMaxDistance() * travelCost;;
		this.adjustTimeWindows();
		this.isFeasible();
	}

	// adjust time windows so the earliest time (e) is just reachable when leaving
	// from the nearest depot at time 0
	private void adjustTimeWindows() {
		Logger.debug("Instance {000}: adjusting time windows", this.index);
		for (Request r : requests) {
			Node near = nearestDepot.get(r.dropoffNode);
			r.dropoffNode.e = Math.max(r.dropoffNode.e, this.distanceBetween(r.dropoffNode, near));

			near = nearestDepot.get(r.pickupNode);
			r.pickupNode.e = Math.max(r.pickupNode.e, this.distanceBetween(r.pickupNode, near));
		}
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

	/*
	 * Calculates the nearest depot, its distance and associated costs for each node
	 */
	private void calculateNearestDepots(List<Node> allNodes) {
		Logger.debug("Instance {000}: calculating nearest depot", this.index);
		for (Node a : allNodes) {
			// we do not skip the depots
			// calculate nearest depot for all other nodes
			Node nearest = null;
			double distance = -1;
			for (Node d : depots) {
				double tmpDist = distanceBetween(a, d);
				if (distance == -1 || tmpDist < distance) {
					nearest = d;
					distance = tmpDist;
				}
			}
			nearestDepot.put(a, nearest);
		}
	}

	// verify that the problem has a feasible solution
	public boolean isFeasible() {
		boolean feasible = true;
		for (Request r : requests) {
			if (distanceBetween(r.pickupNode, r.dropoffNode) > r.L) {
				Logger.warn(
						"Instance {000}: request {000} is infeasible, the travel distance between pickup ({}, {}) and dropoff ({}, {}) is {00.00} > {00.00} = L",
						this.index, r.id, r.pickupNode.x, r.pickupNode.y, r.dropoffNode.x, r.dropoffNode.y,
						this.distanceBetween(r.pickupNode, r.dropoffNode), (double) r.L);
				feasible = false;
			}
			if (r.dropoffNode.e - r.pickupNode.l > r.L) {
				Logger.warn(
						"Instance {000}: request {000} is infeasible, the minimum time between pickup and dropoff (because of the time windows) is {00.00} - {00.00} = {00.00} > {00.00} = L",
						this.index, r.id, r.dropoffNode.e, r.pickupNode.l, r.dropoffNode.e - r.pickupNode.l,
						(double) r.L);
				feasible = false;
			}
		}
		if (feasible) {
			Logger.info("Instance {000}: all requests are feasible", this.index);
		}
		return feasible;
	}

	/*
	 * Calculates an array with distances between nodes, so we don't have to do on
	 * the fly calculations
	 */
	private void preCalcDistances(List<Node> allNodes) {
		Logger.debug("Instance {000}: calculating distance and cost matrices", this.index);
		distanceMatrix = new double[allNodes.size()][allNodes.size()];

		// iterate over all node to calculate distances/costs
		for (Node a : allNodes) {
			for (Node b : allNodes) {
				if (a == b) {
					distanceMatrix[a.id - 1][b.id - 1] = 0; // ids are 1-indexed, arrays 0-indexed
				} else {
					distanceMatrix[a.id - 1][b.id - 1] = Math
							.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
				}
			}
		}
	}

	/* Prints the distance matrix with node ids */
	@SuppressWarnings("unused")
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
		// try to retrieve it before calculating it
		Node nearest = nearestDepot.get(a);
		return nearest;
	}

	// finds the distance to the nearest depot for some node a
	public double getDistanceToNearestDepot(Node a) {
		return distanceBetween(a, getNearestDepot(a));
	}
	 public double getMaxDistance(){
		double maxDist = -1;
		for(int i = 0; i < distanceMatrix.length;i++){
			for(int j = 0; j < distanceMatrix[0].length;j++){
				if(distanceMatrix[i][j] > maxDist){
					maxDist = distanceMatrix[i][j];
				}
			}
		}
		return maxDist;
	 }
	/*
	 * should probably be a check on this stuff to prevent NPEs, but that will cost
	 * computer time
	 */
	public double distanceBetween(Node a, Node b) {
		return distanceBetween(a.id, b.id);
	}

	private double distanceBetween(int id1, int id2) {
		return distanceMatrix[id1 - 1][id2 - 1];
	}

	public double costBetween(Node a, Node b) {
		return costBetween(a.id, b.id);
	}

	private double costBetween(int id1, int id2) {
		return travelCost * distanceMatrix[id1 - 1][id2 - 1];
	}

}
