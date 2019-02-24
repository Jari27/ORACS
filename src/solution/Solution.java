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

	static final String GROUP = "Group #";

	int index;

	Problem p;

	double cost = 0;

	public List<Route> routes = new ArrayList<>();

	// TODO keep track of transfers, requests

	/**
	 * @param p Associated problem instance
	 */
	public Solution(Problem p) {
		this.index = p.index;
		this.p = p;
	}

	/**
	 * Creates an initial solution where each request is handled by a separate
	 * vehicle/route. If the problem is infeasible, it will return a pretty close
	 * solution but log warnings.
	 */
	public void createInitialSolution() {
		Logger.debug("Trying to find a feasible solution for problem instance {000}", this.index);
		this.cost = 0;
		for (Request r : p.requests) {
			double dist = p.distanceBetween(r.pickupNode, r.dropoffNode);
			if (dist > r.L) {
				Logger.warn("No solution for request {000}, distance between pickup and dropoff too large", r.id);
				Logger.warn("Instance {000} is infeasible!", index);
				Logger.warn("We will try to find a close starting solution for testing purposes.");
				// return;
			}
			Route route = new Route();

			RouteNode pickup = new RouteNode(r.pickupNode, RouteNodeType.PICKUP);
			RouteNode dropoff = new RouteNode(r.dropoffNode, RouteNodeType.DROPOFF);

			// TODO keep track of requests and transfer to easily check feasibility (i.e.,
			// update when setting any routenode)

			// arrive early and wait\
			// preprocessing ensures this earliest time window is just reachable
			pickup.setArrival(pickup.getAssociatedNode().getE()); 
			pickup.setStartOfS(pickup.getArrival(), false);
			pickup.setNumPas(1);

			dropoff.setArrival(pickup.getDeparture() + p.distanceBetween(pickup.getAssociatedNode(), dropoff.getAssociatedNode())); // set arrival to departure + travel time
			dropoff.setStartOfS(Math.max(dropoff.getAssociatedNode().getE(), dropoff.getArrival()), false); // set start of s as early as possible
			dropoff.setNumPas(0);

			// ensure max ride time is satisfied
			if (dropoff.getStartOfS() - pickup.getDeparture() > r.L) {
				Logger.debug(
						"Instance {000}: adjusting start of service of pickup time for request {000}. Current: {0.00}",
						index, r.id, pickup.getStartOfS());
				
				double newStart = pickup.getArrival() + Math.min(dropoff.getWaiting(), pickup.getSlack()); // adjust
				pickup.setArrival(newStart);
				pickup.setStartOfS(newStart, true);
				dropoff.setArrival(pickup.getDeparture() + p.distanceBetween(dropoff.getAssociatedNode(), pickup.getAssociatedNode()));
				dropoff.setStartOfS(Math.max(dropoff.getArrival(), dropoff.getAssociatedNode().getE()), true);
			} else {
				Logger.debug(
						"Instance {000}: initial solution already okay. Pickup.SoS: {0.00} <= {0.00} <= {0.00}, dropoff.arr = {0.00}, dropoff.SoS: {0.00} <= {0.00} <= {0.00}, length = {0.00}, r.L = {0.00}",
						index, pickup.getAssociatedNode().getE(), pickup.getStartOfS(), pickup.getAssociatedNode().getL(),
						dropoff.getArrival(), dropoff.getAssociatedNode().getE(), dropoff.getStartOfS(), dropoff.getAssociatedNode().getL(),
						dropoff.getStartOfS() - pickup.getDeparture(), r.L);
			}

			RouteNode depotStart = new RouteNode(r.pickupNode.getNearestDepot(), RouteNodeType.DEPOT_START);
			depotStart.setDeparture(pickup.getArrival() - p.distanceBetween(depotStart.getAssociatedNode(), pickup.getAssociatedNode()));

			RouteNode depotEnd = new RouteNode(r.dropoffNode.getNearestDepot(), RouteNodeType.DEPOT_END);
			depotEnd.setArrival(dropoff.getDeparture() + p.distanceBetween(depotEnd.getAssociatedNode(), dropoff.getAssociatedNode()));

			route.add(depotStart);
			route.add(pickup);
			route.add(dropoff);
			route.add(depotEnd);

			routes.add(route);

			for (int i = 0; i < route.size() - 1; i++) {
				cost += p.costBetween(route.get(i).getAssociatedNode(), route.get(i + 1).getAssociatedNode());
			}
		}
		Logger.info("Found an initial solution for problem {000} with cost {0.00}", index, cost);
		logSolution();
		try {
			exportSolution(true);
		} catch (FileNotFoundException e) {
			Logger.error("Cannot export file for instance {000}", this.index);
			Logger.error(e);
		}
	}

	/**
	 * Writes a solution to the log. TODO: Add handling of transfers
	 */
	public void logSolution() {
		int index = 1;
		for (Route r : routes) {
			Logger.debug("Vehicle {000}", index);
			for (RouteNode rn : r) {
				switch (rn.getType()) {
				case DEPOT_START:
					Logger.debug("Leave depot {000} at {0.00}", rn.getAssociatedNode().id, rn.getDeparture());
					break;
				case PICKUP:
					Logger.debug(
							"Arrive at pickup  {000} at {0.00}, wait {0.00}, start service at {0.00}, leave at {0.00}",
							rn.getAssociatedNode().id, rn.getArrival(), rn.getWaiting(), rn.getStartOfS(), rn.getDeparture());
					break;
				case DROPOFF:
					Logger.debug(
							"Arrive at dropoff {000} at {0.00}, wait {0.00}, start service at {0.00}, leave at {0.00}",
							rn.getAssociatedNode().id, rn.getArrival(), rn.getWaiting(), rn.getStartOfS(), rn.getDeparture());
					break;
				case DEPOT_END:
					Logger.debug("Arrive at depot {000} at {0.00}", rn.getAssociatedNode().id, rn.getArrival());
					break;
				default:
					Logger.warn("Invalid routenode");
					break;
				}
			}
			index++;
		}
	}

	/**
	 * Exports a solution as CSV to the default location
	 * (/solutions/[initial_]oracs_{id}.csv).
	 * 
	 * @throws FileNotFoundException
	 */
	public void exportSolution(boolean isInitial) throws FileNotFoundException {
		// TODO handle transfers
		String file = isInitial ? String.format("solutions/initial_oracs_%d.csv", index)
				: String.format("solutions/oracs_%d.csv", index);
		try (PrintWriter writer = new PrintWriter(new File(file))) {
			writer.println(GROUP);
			writer.println(index);
			writer.println(String.format("%.2f", cost));
			writer.println(routes.size());
			// print routes
			for (Route r : routes) {
				// empty line
				writer.println();
				// order of nodes
				for (int i = 0; i < r.size(); i++) {
					RouteNode rn = r.get(i);
					if (rn.getType() == RouteNodeType.TRANSFER_DROPOFF || rn.getType() == RouteNodeType.TRANSFER_PICKUP) {
						// writer.print(String.format("1%03d%03d", rn.associatedNode.id, passenger
						// person thing));
					}
					writer.print(rn.getAssociatedNode().id);
					// print commas but no trailing commas
					if (i < r.size() - 1) {
						writer.print(",");
					}
				}
				writer.println();
				// service time starts
				for (int i = 0; i < r.size(); i++) {
					RouteNode rn = r.get(i);
					if (rn.getType() == RouteNodeType.TRANSFER_DROPOFF || rn.getType() == RouteNodeType.TRANSFER_PICKUP) {
						// writer.print(String.format("1%03d%03d", rn.associatedNode.id, passenger
						// person thing));
					}
					if (rn.getType() == RouteNodeType.DEPOT_START) {
						writer.print(rn.getDeparture());
					} else if (rn.getType() == RouteNodeType.DEPOT_END) {
						writer.print(rn.getArrival());
					} else {
						writer.print(rn.getStartOfS());
					}
					// print commas but no trailing commas
					if (i < r.size() - 1) {
						writer.print(",");
					}
				}
				writer.println();
			}
		}
	}

	
	/**
	 * Makes a deep copy of the current solution. Each object (except the problem instance) is copied in turn.
	 * 
	 * @return a copy of this solution object
	 */
	public Solution copy() {
		Solution next = new Solution(this.p);
		next.cost = cost;
		for (Route oldR : routes) {
			Route newR = oldR.copy();
			next.routes.add(newR);
		}
		return next;
	}
}
