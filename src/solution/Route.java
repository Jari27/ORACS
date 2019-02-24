package solution;

import java.util.LinkedList;
import java.util.ListIterator;

@SuppressWarnings("serial")
public class Route extends LinkedList<RouteNode>{

	/**
	 * Makes a deep copy of this route. All references to RouteNodes are new RouteNodes.
	 * @return a copy of this route
	 */
	public Route copy() {
		Route newR = new Route();
		for (ListIterator<RouteNode> iter = this.listIterator(); iter.hasNext(); ) {
			RouteNode rnNew = iter.next().copy();
			newR.add(rnNew);
		}
		return newR;
	}

	// TODO : some function to calculate feasible times given a route
	// TODO some function to check whether an insertion is valid?
}
