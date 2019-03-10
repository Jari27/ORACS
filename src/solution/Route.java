package solution;


import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.pmw.tinylog.Logger;

import problem.Problem;

@SuppressWarnings("serial")
public class Route extends LinkedList<RouteNode>{
	
	public int vehicleId = -1;
	private double cost = 0;
	private boolean routeChanged = true;
	
	public Route(int vehicleId) {
		this.vehicleId = vehicleId;
	}
	
	public double getCost(Problem p) {
		if (routeChanged) {
			Logger.trace("Calculating new cost of Route {000}. Old: {00.00}", vehicleId, cost);
			
			// depot costs			
			problem.Node first = this.getFirst().getAssociatedNode();
			problem.Node last = this.getLast().getAssociatedNode();
			
			cost = p.costBetween(first, p.getNearestDepot(first)) + p.costBetween(last, p.getNearestDepot(last));
			
			for (int i = 0; i < this.size() - 1; i++) {
				cost += p.costBetween(this.get(i).getAssociatedNode(), this.get(i+1).getAssociatedNode());
			}
			routeChanged = false;
		}
		Logger.trace("(New) cost: {00.00}", cost);
		return cost;
	}
	
	public void setRouteChanged() {
		this.routeChanged = true;
	}
	
	public void setCost(double cost) {
		this.cost = cost;
	}
	
	public void setRouteUnchanged(){
		this.routeChanged = false;
	}

	public Route copy() {
		Route r = new Route(this.vehicleId);
		r.routeChanged = this.routeChanged;
		r.cost = this.cost;
		for (RouteNode cur : this) {
			RouteNode next = cur.copy();
			r.add(next);
		}
		return r;
	}
	
	public void logRoute() {
		Logger.debug("Vehicle {000}", this.vehicleId);
		for (RouteNode rn : this) {
			Logger.debug(
					"Arrive at {} {000} at {0.00}, start service at {0.00}, leave at {0.00}", rn.getType().toString(),
					rn.getAssociatedNode().id, rn.getArrival(), rn.getStartOfS(), rn.getDeparture());
		}
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#add(int, java.lang.Object)
	 */
	@Override
	public void add(int index, RouteNode element) {
		this.setRouteChanged();
		super.add(index, element);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#add(java.lang.Object)
	 */
	@Override
	public boolean add(RouteNode e) {
		this.setRouteChanged();
		return super.add(e);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#addAll(java.util.Collection)
	 */
	@Override
	public boolean addAll(Collection<? extends RouteNode> c) {
		this.setRouteChanged();
		return super.addAll(c);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#addAll(int, java.util.Collection)
	 */
	@Override
	public boolean addAll(int index, Collection<? extends RouteNode> c) {
		this.setRouteChanged();
		return super.addAll(index, c);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#addFirst(java.lang.Object)
	 */
	@Override
	public void addFirst(RouteNode e) {
		this.setRouteChanged();
		super.addFirst(e);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#addLast(java.lang.Object)
	 */
	@Override
	public void addLast(RouteNode e) {
		this.setRouteChanged();
		super.addLast(e);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#clear()
	 */
	@Override
	public void clear() {
		this.setRouteChanged();
		super.clear();
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#offer(java.lang.Object)
	 */
	@Override
	public boolean offer(RouteNode e) {
		this.setRouteChanged();
		return super.offer(e);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#offerFirst(java.lang.Object)
	 */
	@Override
	public boolean offerFirst(RouteNode e) {
		this.setRouteChanged();
		return super.offerFirst(e);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#offerLast(java.lang.Object)
	 */
	@Override
	public boolean offerLast(RouteNode e) {
		this.setRouteChanged();
		return super.offerLast(e);
	}

	// override all methods that change the Route so we can ensure it will recalculate the cost after a change
	
	/* (non-Javadoc)
	 * @see java.util.LinkedList#poll()
	 */
	@Override
	public RouteNode poll() {
		this.setRouteChanged();
		return super.poll();
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#pollFirst()
	 */
	@Override
	public RouteNode pollFirst() {
		this.setRouteChanged();
		return super.pollFirst();
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#pollLast()
	 */
	@Override
	public RouteNode pollLast() {
		this.setRouteChanged();
		return super.pollLast();
	}

	// override all methods that change the Route so we can ensure it will recalculate the cost after a change
	
	/* (non-Javadoc)
	 * @see java.util.LinkedList#pop()
	 */
	@Override
	public RouteNode pop() {
		this.setRouteChanged();
		return super.pop();
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#push(java.lang.Object)
	 */
	@Override
	public void push(RouteNode e) {
		this.setRouteChanged();
		super.push(e);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#remove()
	 */
	@Override
	public RouteNode remove() {
		this.setRouteChanged();
		return super.remove();
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#remove(int)
	 */
	@Override
	public RouteNode remove(int index) {
		this.setRouteChanged();
		return super.remove(index);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#remove(java.lang.Object)
	 */
	@Override
	public boolean remove(Object o) {
		this.setRouteChanged();
		return super.remove(o);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#removeFirst()
	 */
	@Override
	public RouteNode removeFirst() {
		this.setRouteChanged();
		return super.removeFirst();
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#removeFirstOccurrence(java.lang.Object)
	 */
	@Override
	public boolean removeFirstOccurrence(Object o) {
		this.setRouteChanged();
		return super.removeFirstOccurrence(o);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#removeLast()
	 */
	@Override
	public RouteNode removeLast() {
		this.setRouteChanged();
		return super.removeLast();
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#removeLastOccurrence(java.lang.Object)
	 */
	@Override
	public boolean removeLastOccurrence(Object o) {
		this.setRouteChanged();
		return super.removeLastOccurrence(o);
	}

	// override all methods that change the Route so we can ensure it will recalculate the cost after a change
	
	/* (non-Javadoc)
	 * @see java.util.Collection#removeIf(java.util.function.Predicate)
	 */
	@Override
	public boolean removeIf(Predicate<? super RouteNode> filter) {
		this.setRouteChanged();
		return super.removeIf(filter);
	}

	/* (non-Javadoc)
	 * @see java.util.AbstractCollection#removeAll(java.util.Collection)
	 */
	@Override
	public boolean removeAll(Collection<?> c) {
		this.setRouteChanged();
		return super.removeAll(c);
	}

	// override all methods that change the Route so we can ensure it will recalculate the cost after a change
	
	/* (non-Javadoc)
	 * @see java.util.List#replaceAll(java.util.function.UnaryOperator)
	 */
	@Override
	public void replaceAll(UnaryOperator<RouteNode> operator) {
		this.setRouteChanged();
		super.replaceAll(operator);
	}

	/* (non-Javadoc)
	 * @see java.util.AbstractCollection#retainAll(java.util.Collection)
	 */
	@Override
	public boolean retainAll(Collection<?> c) {
		this.setRouteChanged();
		return super.retainAll(c);
	}

	/* (non-Javadoc)
	 * @see java.util.LinkedList#set(int, java.lang.Object)
	 */
	@Override
	public RouteNode set(int index, RouteNode element) {
		this.setRouteChanged();
		return super.set(index, element);
	}
}
