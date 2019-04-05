package heuristics.destroy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import problem.Node;
import problem.Problem;
import problem.Request;
import solution.Solution;
import solution.SolutionRequest;

public class ClusterRemoval extends DestroyHeuristic {
	
	Random r;

	public ClusterRemoval(Problem p, Random r) {
		super(p);
		this.r = r;
	}

	@Override
	public List<Integer> destroy(Solution s, int number) {
		SolutionRequest sr = s.requests.get(r.nextInt(s.requests.size()));
		Node root;
		boolean isPickup = true;
		if (r.nextBoolean()) {
			root = sr.pickup.associatedNode;
		} else {
			root = sr.dropoff.associatedNode;
			isPickup = false;
		}
		List<Node> ordered = new ArrayList<>();
		for (Request r : problem.requests) {
			if (r.pickupNode == root || r.dropoffNode == root) {
				continue;
			}
			if (isPickup) {
				ordered.add(r.pickupNode);
			} else {
				ordered.add(r.dropoffNode);
			}
		}
		ordered.sort(new Comparator<Node>() {
			@Override
			public int compare(Node lhs, Node rhs) {
				double delta = problem.distanceBetween(lhs, root) - problem.distanceBetween(rhs, root);
				if (delta < 0) {
					return -1;
				} else if (delta > 0) {
					return 1;
				}
				return 0;
			}
		});
		
		List<Integer> destroyedRequestIds = new ArrayList<>();
		
		while (number > 0) {
			double y = r.nextDouble();
			double yp = Math.pow(y, 9); // from Masson 2013
			int indexToRemove = (int) Math.max(Math.floor((yp * (problem.numRequests - destroyedRequestIds.size() - 1))), 0);
			Node nodeToRemove = ordered.remove(indexToRemove);
			int idToRemove = isPickup ? nodeToRemove.id : nodeToRemove.id - problem.numRequests;
			SolutionRequest removed = s.requests.get(idToRemove - 1);
			destroyedRequestIds.add(removed.id);
			destroySpecific(s, removed.id);
			number--;
		}
		return destroyedRequestIds;
	}

}
