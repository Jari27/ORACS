package main;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.pmw.tinylog.Logger;

import problem.Problem;

public class ALNSRunner extends Thread {
	
	private List<Problem> problems = new ArrayList<>();
	
	public void assignProblem(Problem p) {
		problems.add(p);
	}
	
	@Override
	public void run() {
		long start = System.currentTimeMillis();
		Logger.info("Started at {}", new Date().toString());
		for (Problem p : problems) {
			ALNS algo = new ALNS(p);
			algo.run();
		}
		Logger.info("Finished at {}", new Date().toString());
		Logger.info("Runtime {}s for this thread", (System.currentTimeMillis() - start) / 1000);
	}
	
}
