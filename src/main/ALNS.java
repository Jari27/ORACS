/**
 * 
 */
package main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.pmw.tinylog.Logger;

import heuristics.destroy.DestroyHeuristic;
import heuristics.destroy.RandomDestroy;
import heuristics.repair.GreedyRepairNoTransfer;
import heuristics.repair.RepairHeuristic;
import problem.Problem;
import solution.Solution;
import solution.SolutionRequest;

/**
 * @author Jari Meevis
 *
 */
public class ALNS implements Runnable {
	
	// ALNS settings
	private static final int MAX_IT = 50;
	private static final double T_START = 10;
	private double temp = T_START;
	
	// Config settings
	private static final int NUM_DESTROY_HEURISTICS = 1;
	private static final int NUM_REPAIR_HEURISTICS = 1;
	
	private static final int ITERATIONS_BEFORE_FORCED_CHANGE = 25;
	
	private static final int INITIAL_WEIGHT = 100;
	
	private Problem p;
	private List<Solution> oldSolutions = new ArrayList<>();
	
	private Random rand;
	private long seed = -1;
	
	private Solution currentSol;
	
	DestroyHeuristic[] destroyHeuristics = new DestroyHeuristic[NUM_DESTROY_HEURISTICS];
	RepairHeuristic[] repairHeuristics = new RepairHeuristic[NUM_REPAIR_HEURISTICS];
	
	int[] weightDestroy = new int[NUM_DESTROY_HEURISTICS];
	int[] weightRepair = new int[NUM_REPAIR_HEURISTICS];
	
	public ALNS(Problem p) {
		this.p = p;
		this.currentSol = new Solution(p);
		this.currentSol.createInitialSolution();
		this.oldSolutions.add(currentSol);
		this.seed = System.currentTimeMillis(); // to allow printing
		this.rand = new Random(this.seed);
		
		this.initHeuristics();
	}
	
	private void initHeuristics() {
		// destroy
		DestroyHeuristic random = new RandomDestroy(p, rand);
		destroyHeuristics[0] = random;
		
		// repair
		RepairHeuristic greedyNoTransfer = new GreedyRepairNoTransfer(p);
		repairHeuristics[0] = greedyNoTransfer;
		
		// weights
		for (int i = 0; i < NUM_DESTROY_HEURISTICS; i++) {
			weightDestroy[i] = INITIAL_WEIGHT;
		}
		for (int i = 0; i < NUM_REPAIR_HEURISTICS; i++) {
			weightRepair[i] = INITIAL_WEIGHT;
		}
	}
	
	private boolean accept(double oldCost, double newCost, double T) {
		if (newCost < oldCost) return true;
		if (T == 0) return newCost < oldCost;
		
		double annealing = Math.exp(-(newCost - oldCost)/T);
		if (annealing >= rand.nextDouble()) {
			return true;
		}
		return false;
	}
	
	private int selectRepair() {
		double[] cumWeight = new double[NUM_REPAIR_HEURISTICS];
        cumWeight[0] = weightRepair[0];
        
        for (int i = 1; i < NUM_REPAIR_HEURISTICS; i++) {
            cumWeight[i] = cumWeight[i - 1] + weightRepair[i];
        }

        double randomWeight = rand.nextDouble() * cumWeight[NUM_REPAIR_HEURISTICS - 1];
        int index = Arrays.binarySearch(cumWeight, randomWeight);
        if (index < 0) {
        	// Convert negative insertion point to array index.
        	index = Math.abs(index + 1);
        }
        return index;
	}
	
	private int selectDestroy() {
		double[] cumWeight = new double[NUM_DESTROY_HEURISTICS];
        cumWeight[0] = weightDestroy[0];
        
        for (int i = 1; i < NUM_DESTROY_HEURISTICS; i++) {
            cumWeight[i] = cumWeight[i - 1] + weightDestroy[i];
        }

        double randomWeight = rand.nextDouble() * cumWeight[NUM_DESTROY_HEURISTICS - 1];
        int index = Arrays.binarySearch(cumWeight, randomWeight);
        if (index < 0) {
        	// Convert negative insertion point to array index.
        	index = Math.abs(index + 1);
        }
        return index;
	}
	
	private double nextTemp() {
		temp = Math.max(temp - T_START/MAX_IT, 0);
		return temp;
	}

	@Override
	public void run() {
		Logger.info("SEED: {}", this.seed);
		Logger.info("Initial cost: {00.00}", currentSol.getCost());
		
		double currentCost = Double.POSITIVE_INFINITY;
		
		int iterationsWithoutImprovement = 0;
		
		for (int i = 1; i <= MAX_IT; i++) {
			Logger.info("ITERATION {}", i);
			
			int destroyId = selectDestroy();
			int repairId = selectRepair();
			DestroyHeuristic destroy = destroyHeuristics[destroyId];
			RepairHeuristic repair = repairHeuristics[repairId];
			
			if (iterationsWithoutImprovement > ITERATIONS_BEFORE_FORCED_CHANGE) {
				// TODO force a big change
			}
			
			Solution copy = currentSol.copy(); // never modify currentSol
			
			SolutionRequest destroyed = destroy.destroySingle(copy); // this always works
			
			Logger.info("Destroyed request {000}. Trying insertion.", destroyed.associatedRequest.id);
			if (!repair.repair(destroyed, copy)) {
				// could not repair
				Logger.info("{} yielded no valid solution. Going to next iteration.", repair);
				Logger.info("Current cost: {00.00}", currentCost);
				continue;
			}
			// we have a repaired solution
			// TODO problems: destroy and insert in same route always accepts (is this a problem?)
			double newCost = copy.getCost();
			Logger.info("Cost of new solution: {00.00}", newCost);
			copy.logSolution();
			
			if (newCost < currentCost  || accept(currentCost, newCost, nextTemp())) {
				Logger.info("Accepted new solution {}", (newCost < currentCost ? "" : "(simulated annealing)"));
				currentCost = newCost;
				currentSol = copy;
				oldSolutions.add(copy);
				iterationsWithoutImprovement = 0;	
			} else {
				Logger.info("No feasible insertion for request {000}.", destroyed.associatedRequest.id);
				Logger.info("Current cost: {00.00}", currentCost);
				iterationsWithoutImprovement++;
			}
		}
	}

	
//	public void destroyMostExpensiveRequest(Solution solution){
//		int expensiveRequestIndex = getMostExpensiveRequest(solution);
//		destroyRequest(solution, expensiveRequestIndex);
//	}
//	
//	//I think the first request has a place in the list of 0, but it has an id of 1??
//	public int getMostExpensiveRequest(Solution solution){
//		double costSolution = solution.getCost();
//		double highestCost = 0;
//		int index = 0;
//		int indexExpensiveRequest = 0;
//		for (SolutionRequest sr : currentSol.requests){
//			int id = index +1;
//			Solution copy = solution.copy();
//			destroyRequest(copy, index);
//			double newCost = copy.getCost();
//			double costRequest = costSolution - newCost;
//			if(costRequest > highestCost){	
//				highestCost = costRequest;
//				indexExpensiveRequest = index;
//				Logger.debug("Currently, The request with the highest cost is request {000}, with a cost of {000}", id, highestCost);
//			}
//			index++;
//		}
//		Logger.debug("The final request with the highest cost is request {000}, with a cost of {000}", indexExpensiveRequest+1, highestCost);
//		return indexExpensiveRequest;
//	}
	
}
