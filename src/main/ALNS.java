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

import heuristics.destroy.CloseRandomTransfer;
import heuristics.destroy.DestroyHeuristic;
import heuristics.destroy.RandomDestroy;
import heuristics.repair.GreedyNoTransferRepair;
import heuristics.repair.RepairHeuristic;
import heuristics.repair.BestInsertionWithTransfer;
import problem.Problem;
import solution.Solution;

/**
 * @author Jari Meevis
 *
 */
public class ALNS implements Runnable {
	
	// ALNS settings
	private static final double ETA = 0.025; // noise in objec. function multiplier
	private static final int MAX_IT = 1000;
	private double coolingRate = 0.99975;
	private static final double W = 0.05; // a new solution will initially be accepted with probability 50% if it is this much worse than the old 
	private double temp;
	
	
	// Config settings
	private static final int NUM_DESTROY_HEURISTICS = 2;
	private static final int NUM_REPAIR_HEURISTICS = 2;
	
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
	
	double[] smoothedWeightDestroy = new double[NUM_DESTROY_HEURISTICS];
	double[] smoothedWeightRepair = new double[NUM_REPAIR_HEURISTICS];
	
	int[] segmentPointsObjectiveNoise = new int[2];
	int[] segmentNumUsedObjectiveNoise = new int[2];
	double[] smoothedWeightObjectiveNoise = new double[2];
	
	public ALNS(Problem p) {
		this.p = p;
		this.currentSol = new Solution(p);
		this.currentSol.createInitialSolution();
		this.bestSol = this.currentSol;
		this.acceptedSolutions.add(currentSol);
		this.seed = 1553532450933L;
//		this.seed = System.currentTimeMillis(); // to allow printing
		
		this.rand = new Random(this.seed);
		
		double old = currentSol.getCost();
		this.temp = - W * old / Math.log(0.5);
		this.coolingRate = Math.pow(0.1, 1.0/MAX_IT);
		
		this.initHeuristics();
	}
	
	private void initHeuristics() {
		// destroy
		DestroyHeuristic random = new RandomDestroy(p, rand);
		destroyHeuristics[0] = random;
		DestroyHeuristic closeRandomTransfer = new CloseRandomTransfer(p, rand);
		destroyHeuristics[1] = closeRandomTransfer;
		
		// repair
		RepairHeuristic greedyNoTransferRepair = new GreedyNoTransferRepair(p);
		repairHeuristics[0] = greedyNoTransferRepair;
		RepairHeuristic bestInsertionWithTransfer = new BestInsertionWithTransfer(p, rand);
		repairHeuristics[1] = bestInsertionWithTransfer;
		
		
		// weights
		for (int i = 0; i < NUM_DESTROY_HEURISTICS; i++) {
			smoothedWeightDestroy[i] = 1;
			segmentPointsDestroy[i] = 0;
			segmentNumUsedDestroy[i] = 1;
		}
		for (int i = 0; i < NUM_REPAIR_HEURISTICS; i++) {
			smoothedWeightRepair[i] = 1;
			segmentPointsRepair[i] = 0;
			segmentNumUsedRepair[i] = 1;
		}
		
		for (int i = 0; i < 2; i++) {
			smoothedWeightObjectiveNoise[i] = 1;
			segmentPointsObjectiveNoise[i] = 0;
			segmentNumUsedObjectiveNoise[i] = 1;
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
	
	private int selectObjectiveNoise() {
		double total = smoothedWeightObjectiveNoise[0] + smoothedWeightObjectiveNoise[1];
		if (rand.nextDouble() * total <= smoothedWeightObjectiveNoise[0]) {
			return 0;
		}
		return 1;
	}

	private double nextTemp() {
		temp = coolingRate * temp;
		return temp;
	}

	@Override
	public void run() {
		Logger.info("Problem instance {}: SEED: {}", p.index, this.seed);
		Logger.info("Problem instance {}: Initial cost: {00.00}", p.index, currentSol.getCost());
		
		double currentCost = Double.POSITIVE_INFINITY;
		double bestCost = Double.POSITIVE_INFINITY;
		
		int iterationsWithoutImprovement = 0;
		
		for (int i = 1; i <= MAX_IT; i++) {
			Logger.debug("Problem instance {}: ITERATION {}", p.index, i);
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
			
			int objectiveNoiseId = selectObjectiveNoise();
			segmentNumUsedObjectiveNoise[objectiveNoiseId]++;
			
			Solution copy = currentSol.copy(); // never modify currentSol
			
			List<Integer> destroyed = null;
			if (iterationsWithoutImprovement > ITERATIONS_BEFORE_FORCED_CHANGE) {
				Logger.info("Doing big destruction");
				destroy.destroy(copy, rand.nextInt(2 *(int) Math.round(p.numRequests * 0.05 + 1)));
			} else {
				destroyed = destroy.destroy(copy, rand.nextInt((int)Math.round(p.numRequests * 0.05 + 1))); // this always works
			}
			
			copy.calcTightWindows();
			Logger.debug("Problem instance {}: Finished destroying the solution.", p.index);

			if (!repair.repair(copy, destroyed)) {
				// could not repair
				Logger.debug("Problem instance {}: {} yielded no valid solution. Going to next iteration.", p.index, repair);
				Logger.debug("Problem instance {}: Current cost: {00.00}. Best cost: {00.00}", p.index, currentCost, bestCost);
				continue;
			}
			
			if (!copy.isFeasibleVerify(false) || copy.hasOrphanRouteNodes()) {
				Logger.warn("Problem instance {}: Found an invalid solution!", p.index);
				break;
			}
			
			// we have a repaired solution
			double newCost = copy.getCost();
			double adjustedCost = newCost;
			
			if (objectiveNoiseId == 1) {
				adjustedCost = applyNoise(adjustedCost);
			}
			
			if (newCost < currentCost  || accept(currentCost, adjustedCost, nextTemp())) {
				Logger.debug("Problem instance {}: Accepted new solution {}", p.index, (adjustedCost < currentCost ? "" : "(simulated annealing)"));
				// update segment weights
				segmentPointsDestroy[destroyId] += 15; // total: +15
				segmentPointsRepair[repairId] += 15;
				segmentPointsObjectiveNoise[objectiveNoiseId] += 15;
				if (newCost < currentCost) {
					segmentPointsDestroy[destroyId] += 5; //total: +20
					segmentPointsRepair[repairId] += 5;
					segmentPointsObjectiveNoise[objectiveNoiseId] += 5;
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
					segmentPointsObjectiveNoise[objectiveNoiseId] += 13;
					Logger.debug("Problem instance {}: New best solution. Cost: {00.00}", p.index, bestCost);
				}
			} else {
				Logger.debug("Problem instance {}: Repaired solution was not accepted.", p.index);
				Logger.debug("Problem instance {}: New cost: {00.00}. Current cost: {00.00}. Best cost: {00.00}", p.index, newCost, currentCost, bestCost);
				iterationsWithoutImprovement++;
			}
		}
		new CloseRandomTransfer(p, rand);
//		Logger.info("CloseRandom had {} tries where transfers were open.", CloseRandomTransfer.successes);
		Logger.info("Problem instance {}. Best solution cost: {00.00}", this.p.index, bestCost);
		try {
			bestSol.exportSolution(false);
		} catch (FileNotFoundException e) {
			Logger.error(e);
			//e.printStackTrace();
		}
	}

	private double applyNoise(double newCost) {
		double n = ETA * (rand.nextDouble() - 0.5) * p.maxCost;
		return Math.max(0, newCost + n);
	}

	private void updateWeights() {
		for (int i = 0; i < NUM_REPAIR_HEURISTICS; i++) {
			smoothedWeightRepair[i] = (double) (SMOOTHING_FACTOR * (double) segmentPointsRepair[i] / (double) segmentNumUsedRepair[i] + (1 - SMOOTHING_FACTOR) * smoothedWeightRepair[i]); 
			segmentPointsRepair[i] = 0;
			segmentNumUsedRepair[i] = 1;
		}
		for (int i = 0; i < NUM_DESTROY_HEURISTICS; i++) {
			smoothedWeightDestroy[i] = (double) (SMOOTHING_FACTOR * (double) segmentPointsDestroy[i] / (double) segmentNumUsedDestroy[i] + (1 - SMOOTHING_FACTOR) * smoothedWeightDestroy[i]); 
			segmentPointsDestroy[i] = 0;
			segmentNumUsedDestroy[i] = 1;
		}
		for (int i = 0; i < 2; i++) {
			smoothedWeightObjectiveNoise[i] = (double) (SMOOTHING_FACTOR * (double) segmentPointsObjectiveNoise[i] / (double) segmentNumUsedObjectiveNoise[i] + (1 - SMOOTHING_FACTOR) * smoothedWeightObjectiveNoise[i]); 
			segmentPointsObjectiveNoise[i] = 0;
			segmentNumUsedObjectiveNoise[i] = 1;
		}
		Logger.info("Problem instance {}: Updated weights. \nRepair {} (). \nDestroy {} (). \nObjective Noise: {} ()", p.index, Arrays.toString(smoothedWeightRepair), Arrays.toString(smoothedWeightDestroy), Arrays.toString(smoothedWeightObjectiveNoise));
	}

	
}
