/**
 * 
 */
package main;

import java.util.ArrayList;
import java.util.List;

import problem.Problem;
import solution.Solution;

/**
 * @author Jari Meevis
 *
 */
public class ALNS implements Runnable {
	
	Problem p;
	List<Solution> solutions = new ArrayList<>();
	
	Solution currentSol;
	
	public ALNS(Problem p) {
		this.p = p;
		this.currentSol = new Solution(p);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		// TODO Auto-generated method stub

	}

}
