package solution;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.pmw.tinylog.Logger;

import problem.Problem;
import problem.Request;

public class Solution {
	
	final String GROUP = "Group #";
	
	int index;
	
	Problem p;
	
	double cost = 0;
	
	List<Route> routes = new ArrayList<>();
	
	// TODO keep track of transfers, requests
	
	public Solution(Problem p) {
		this.index = p.index;
		this.p = p;
		this.createInitialSolution();
	}
	
	void createInitialSolution() {
		Logger.info("Trying to find a feasible solution for problem instance {000}", this.index);
		this.cost = 0;
		for (Request r : p.requests) {
			double dist = p.distanceBetween(r.pickupNode, r.dropoffNode);
			if (dist > r.L) {
				Logger.warn("No solution for request {000}, distance between pickup and dropoff too large", r.id);
				Logger.warn("Instance {000} is infeasible!", index);
				Logger.warn("We will try to find a close starting solution for testing purposes.");
				//return;
			}
			Route route = new Route();
			
			RouteNode pickup = new RouteNode(r.pickupNode, RouteNodeType.PICKUP);
			RouteNode dropoff = new RouteNode(r.dropoffNode, RouteNodeType.DROPOFF);
			
			// TODO encode this with getters and setters to automatically update all values
			
			// arrive early and wait
			pickup.arrival = pickup.associatedNode.getE(); // The timewindow is adjusted to ensure that the earliest time is just reachable when leaving from the nearest depot at time 0
			pickup.setStartOfS(pickup.arrival, false);
			pickup.numPas = 1;
			//pickup.slack = pickup.associatedNode.getE() - pickup.startOfS;
			//pickup.waiting = pickup.startOfS - pickup.arrival;
			
			dropoff.arrival = pickup.departure + p.distanceBetween(pickup.associatedNode, dropoff.associatedNode);
			dropoff.setStartOfS(Math.max(dropoff.associatedNode.getE(), dropoff.arrival), false); // set start of s as early as possible
			dropoff.numPas = 0;
//			dropoff.waiting = dropoff.startOfS - dropoff.arrival;
//			dropoff.slack = dropoff.associatedNode.getL() - dropoff.startOfS;
			
			// ensure max ride time is satisfied
			
			// TODO make this automagically calculate the next one; what happens when we have multiple in a row. Just try and it check feasibility afterwards? 
			// TODO how to move longer routes to ensure they are still feasible when moving them (two by two? keep track of slack in successor nodes)
			// TODO PREVENT STARTING BEFORE TIME 0?
			// TODO find a way to ensure we make dropoffs n shit correctly
			if (dropoff.startOfS - pickup.departure > r.L) {
				double newStart = pickup.arrival + Math.min(dropoff.waiting, pickup.slack); // adjust 
				pickup.setArrival(newStart);
				pickup.setStartOfS(newStart, true);
				dropoff.setArrival(pickup.departure + p.distanceBetween(dropoff.associatedNode, pickup.associatedNode));
				dropoff.setStartOfS(Math.max(dropoff.arrival, dropoff.associatedNode.getE()), true);
			}
			
			
			
			RouteNode depotStart = new RouteNode(r.pickupNode.getNearestDepot(), RouteNodeType.DEPOT_START);
			depotStart.departure = pickup.arrival - p.distanceBetween(depotStart.associatedNode, pickup.associatedNode);
			
			RouteNode depotEnd = new RouteNode(r.dropoffNode.getNearestDepot(), RouteNodeType.DEPOT_END);
			depotEnd.arrival = dropoff.departure + p.distanceBetween(depotEnd.associatedNode, dropoff.associatedNode);
			
			route.route.add(depotStart);
			route.route.add(pickup);
			route.route.add(dropoff);
			route.route.add(depotEnd);
			
			routes.add(route);
			
			for (int i = 0; i < route.route.size() - 1; i++) {
				cost += p.costBetween(route.route.get(i).associatedNode, route.route.get(i+1).associatedNode);
			}
			// TODO test
		}
		Logger.info("Found an initial solution for problem {000} with cost {0.00}", index, cost);
		logSolution();
		try {
			exportSolution();
		} catch (FileNotFoundException e) {
			Logger.error("Cannot export file for instance {000}", this.index);
			Logger.error(e);
		}
	}
	
	public void logSolution() {
		int index = 1;
		for (Route r : routes) {
			Logger.debug("Vehicle {000}", index);
			for (RouteNode rn : r.route) {
				switch(rn.type) {
				case DEPOT_START:
					Logger.debug("Leave depot {000} at {0.00}", rn.associatedNode.id, rn.departure);
					break;
				case PICKUP:
					Logger.debug("Arrive at pickup  {000} at {0.00}, wait {0.00}, start service at {0.00}, leave at {0.00}", rn.associatedNode.id, rn.arrival, rn.waiting, rn.startOfS, rn.departure);
					break;
				case DROPOFF:
					Logger.debug("Arrive at dropoff {000} at {0.00}, wait {0.00}, start service at {0.00}, leave at {0.00}", rn.associatedNode.id, rn.arrival, rn.waiting, rn.startOfS, rn.departure);
					break;
				case DEPOT_END:
					Logger.debug("Arrive at depot {000} at {0.00}", rn.associatedNode.id, rn.arrival);
					break;
				default:
					Logger.warn("Invalid routenode");
					break;
				}
			}
			index++;
		}
	}
	
	public void exportSolution() throws FileNotFoundException {
		try (PrintWriter writer = new PrintWriter(new File(String.format("initial_oracs_%d.csv", index)))) {
			writer.println(GROUP);
			writer.println(index);
			writer.println(String.format("%.2f", cost)); 
			writer.println(routes.size());
			// print routes
			for (Route r : routes) {
				// empty line
				writer.println();
				// order of nodes
				for (int i = 0; i < r.route.size(); i++) {
					RouteNode rn = r.route.get(i);
					if (rn.type == RouteNodeType.TRANSFER_DROPOFF || rn.type == RouteNodeType.TRANSFER_PICKUP) {
						//writer.print(String.format("1%03d%03d", rn.associatedNode.id, passenger person thing));
					}
					writer.print(rn.associatedNode.id);
					// print commas but no trailing commas
					if (i < r.route.size() - 1) {
						writer.print(",");
					}
				}
				writer.println();
				// service time starts
				for (int i = 0; i < r.route.size(); i++) {
					RouteNode rn = r.route.get(i);
					if (rn.type == RouteNodeType.TRANSFER_DROPOFF || rn.type == RouteNodeType.TRANSFER_PICKUP) {
						//writer.print(String.format("1%03d%03d", rn.associatedNode.id, passenger person thing));
					}
					if (rn.type == RouteNodeType.DEPOT_START) {
						writer.print(rn.departure);
					} else if (rn.type == RouteNodeType.DEPOT_END) {
						writer.print(rn.arrival);
					} else {
						writer.print(rn.startOfS);
					}
					// print commas but no trailing commas
					if (i < r.route.size() - 1) {
						writer.print(",");
					}
				}
				writer.println();
			}
		}
	}

	
}
