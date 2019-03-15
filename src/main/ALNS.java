/**
 * 
 */
package main;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.pmw.tinylog.Logger;

import heuristics2.destroy.DestroyHeuristic;
import heuristics2.destroy.RandomDestroy;
import heuristics2.repair.GreedyNoTransferRepair;
import heuristics2.repair.RepairHeuristic;
import problem.Problem;
import solution.Solution;
import solution.SolutionRequest;

/**
 * @author Jari Meevis
 *
 */
public class ALNS implements Runnable {
	
	// ALNS settings
	private static final int MAX_IT = 1000;
	private static final double T_START = 10;
	private double temp = T_START;
	
	// Config settings
	private static final int NUM_DESTROY_HEURISTICS = 1;
	private static final int NUM_REPAIR_HEURISTICS = 1;
	
	private static final double SMOOTHING_FACTOR = 0.1;
	
	private static final int ITERATIONS_BEFORE_FORCED_CHANGE = 25;
	
	private Problem p;
	private List<Solution> acceptedSolutions = new ArrayList<>();
	
	private Random rand;
	private long seed = -1;
	
	private Solution currentSol;
	private Solution bestSol;
	
	DestroyHeuristic[] destroyHeuristics = new DestroyHeuristic[NUM_DESTROY_HEURISTICS];
	RepairHeuristic[] repairHeuristics = new RepairHeuristic[NUM_REPAIR_HEURISTICS];
	
	int[] segmentPointsDestroy = new int[NUM_DESTROY_HEURISTICS];
	int[] segmentPointsRepair = new int[NUM_REPAIR_HEURISTICS];
	
	int[] segmentNumUsedDestroy = new int[NUM_DESTROY_HEURISTICS];
	int[] segmentNumUsedRepair = new int[NUM_REPAIR_HEURISTICS];
	
	int[] smoothedWeightDestroy = new int[NUM_DESTROY_HEURISTICS];
	int[] smoothedWeightRepair = new int[NUM_REPAIR_HEURISTICS];
	
	public ALNS(Problem p) {
		this.p = p;
		this.currentSol = new Solution(p);
		this.currentSol.createInitialSolution();
		this.bestSol = this.currentSol;
		this.acceptedSolutions.add(currentSol);
//		this.seed = System.currentTimeMillis(); // to allow printing
		this.seed = 1552596372812L;
		this.rand = new Random(this.seed);
		
		this.initHeuristics();
	}
	
	private void initHeuristics() {
		// destroy
		DestroyHeuristic random = new RandomDestroy(p, rand);
		destroyHeuristics[0] = random;
		
		// repair
		RepairHeuristic greedyNoTransfer = new GreedyNoTransferRepair(p);
		repairHeuristics[0] = greedyNoTransfer;
		
		// weights
		for (int i = 0; i < NUM_DESTROY_HEURISTICS; i++) {
			smoothedWeightDestroy[i] = 0;
			segmentPointsDestroy[i] = 0;
			segmentNumUsedDestroy[i] = 0;
		}
		for (int i = 0; i < NUM_REPAIR_HEURISTICS; i++) {
			smoothedWeightRepair[i] = 0;
			segmentPointsRepair[i] = 0;
			segmentNumUsedRepair[i] = 0;
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
        cumWeight[0] = smoothedWeightRepair[0];
        
        for (int i = 1; i < NUM_REPAIR_HEURISTICS; i++) {
            cumWeight[i] = cumWeight[i - 1] + smoothedWeightRepair[i];
        }
        
        if (cumWeight[NUM_REPAIR_HEURISTICS - 1] == 0) { // all weights are 0
        	return rand.nextInt(NUM_REPAIR_HEURISTICS); // so select a random one
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
        cumWeight[0] = smoothedWeightDestroy[0];
        
        for (int i = 1; i < NUM_DESTROY_HEURISTICS; i++) {
            cumWeight[i] = cumWeight[i - 1] + smoothedWeightDestroy[i];
        }
        
        if (cumWeight[NUM_DESTROY_HEURISTICS - 1] == 0) { // all weights are 0
        	return rand.nextInt(NUM_DESTROY_HEURISTICS); 
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
		double bestCost = Double.POSITIVE_INFINITY;
		
		int iterationsWithoutImprovement = 0;
		
		for (int i = 1; i <= MAX_IT; i++) {
			Logger.debug("ITERATION {}", i);
			if (i % 100 == 0) {
				updateWeights();
			}
			
			// select heuristic
			int destroyId = selectDestroy();
			int repairId = selectRepair();
			segmentNumUsedRepair[repairId]++;
			segmentNumUsedDestroy[destroyId]++;
			DestroyHeuristic destroy = destroyHeuristics[destroyId];
			RepairHeuristic repair = repairHeuristics[repairId];
			
			if (iterationsWithoutImprovement > ITERATIONS_BEFORE_FORCED_CHANGE) {
				// TODO force a big change
			}
			
			Solution copy = currentSol.copy(); // never modify currentSol
			
			List<Integer> destroyed = destroy.destroy(copy, 1); // this always works
			Logger.debug("Finished destroying the solution.");

			if (!repair.repair(copy, destroyed)) {
				// could not repair
				Logger.debug("{} yielded no valid solution. Going to next iteration.", repair);
				Logger.debug("Current cost: {00.00}. Best cost: {00.00}", currentCost, bestCost);
				continue;
			}
			
			if (!copy.isFeasible() || copy.hasOrphanRouteNodes()) {
				Logger.warn("Found an invalid solution!");
				break;
			}
			
			// we have a repaired solution
			// TODO problems: destroy and insert in same route always accepts (is this a problem?)
			double newCost = copy.getCost();
			
			if (newCost < currentCost  || accept(currentCost, newCost, nextTemp())) {
				Logger.debug("Accepted new solution {}", (newCost < currentCost ? "" : "(simulated annealing)"));
				// update segment weights
				segmentPointsDestroy[destroyId] += 15; // total: +15
				segmentPointsRepair[repairId] += 15;
				if (newCost < currentCost) {
					segmentPointsDestroy[destroyId] += 5; //total: +20
					segmentPointsRepair[repairId] += 5;
				}
				// update new solution
				currentCost = newCost;
				currentSol = copy;
				acceptedSolutions.add(copy);
				iterationsWithoutImprovement = 0;
				
//				copy.logSolution();
				
				if (newCost < bestCost) {
					bestSol = copy;
					bestCost = newCost;
					segmentPointsDestroy[destroyId] += 13; //total: +33
					segmentPointsRepair[repairId] += 13;
					Logger.info("New best solution. Cost: {00.00}", bestCost);
				}
			} else {
				Logger.debug("Repaired solution was not accepted.");
				Logger.debug("New cost: {00.00}. Current cost: {00.00}. Best cost: {00.00}", newCost, currentCost, bestCost);
				iterationsWithoutImprovement++;
			}
		}
		Logger.info("Best solution cost: {00.00}", bestCost);
		try {
			bestSol.exportSolution(false);
		} catch (FileNotFoundException e) {
			Logger.error(e);
			//e.printStackTrace();
		}
	}

	private void updateWeights() {
		for (int i = 0; i < NUM_REPAIR_HEURISTICS; i++) {
			smoothedWeightRepair[i] = (int) Math.round(SMOOTHING_FACTOR * segmentPointsRepair[i] + (1 - SMOOTHING_FACTOR) * smoothedWeightRepair[i]); 
			segmentPointsRepair[i] = 1;
		}
		for (int i = 0; i < NUM_DESTROY_HEURISTICS; i++) {
			smoothedWeightDestroy[i] = (int) Math.round(SMOOTHING_FACTOR * segmentPointsDestroy[i] + (1 - SMOOTHING_FACTOR) * smoothedWeightDestroy[i]); 
			segmentPointsDestroy[i] = 1;
		}
		Logger.info("Updated weights. Repair {}. Destroy {}", smoothedWeightRepair, smoothedWeightDestroy);
	}

	
}
