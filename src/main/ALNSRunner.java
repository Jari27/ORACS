package main;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.pmw.tinylog.Logger;

import problem.Problem;

public class ALNSRunner extends Thread {
	
	private List<Problem> problems = new ArrayList<>();
	private int maxIt = -1;
	
	public void setIterations(int iter) {
		this.maxIt = iter;
	}
	
	public void assignProblem(Problem p) {
		problems.add(p);
	}
	
	@Override
	public void run() {
		Logger.info("Started at {}", new Date().toString());
		for (Problem p : problems) {
			ALNS algo;
			if (maxIt == -1) {
				algo = new ALNS(p);
			} else {
				algo = new ALNS(p, maxIt);
			}
			algo.run();
		}
	}
	
}
