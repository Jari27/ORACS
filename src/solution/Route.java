package solution;


import java.util.Collection;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.pmw.tinylog.Logger;

import problem.PickupNode;
import problem.DropoffNode;
import problem.Request;
import problem.Problem;
import solution.SolutionRequest;

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
			Logger.debug("Calculating new cost of Route {000}. Old: {00.00}", vehicleId, cost);
			cost = 0;
			for (int i = 0; i < this.size() - 1; i++) {
				cost += p.costBetween(this.get(i).getAssociatedNode(), this.get(i+1).getAssociatedNode());
			}
			routeChanged = false;
		}
		Logger.debug("(New) cost: {00.00}", cost);
		return cost;
	}
	
	public void setRouteChanged() {
		this.routeChanged = true;
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
	}
	
	//does not take into account the nr of passengers.. But maybe better to do this in the ALNS, since a route doesnt know in which problem it is
	public boolean timingIsFeasible(){
		Logger.debug("Checking if Route {000} is feasible..", this.vehicleId);
		int index1 = 0;
		int index2 = 0;
		for (ListIterator<RouteNode> l = listIterator(0); l.hasNext();){
			RouteNode cur = l.next();
			index1 += nodeIsFeasible(cur);
			index2 += 1;
		}
		if(index1 == index2){
			Logger.debug("Route {000} is feasible..", this.vehicleId);
			return true;
		}else{
			Logger.debug("Route {000} is unfeasible..", this.vehicleId);
			return false;
		}
	}
		
	public int nodeIsFeasible(RouteNode rn) {
		RouteNodeType type = rn.getType();
		switch(type){
		case DEPOT_START:
			Logger.debug("This is the starting depot, time window always satisfied");
			return 1;
		case PICKUP:
			Request associatedRp = rn.getAssociatedRequest();
			PickupNode pickup = associatedRp.getPickup();
			Logger.debug("This is a pickup node with time window: {00} - {00}", pickup.getE(), pickup.getL());
			if(rn.getStartOfS() >= pickup.getE() && rn.getStartOfS() <= pickup.getL()){
				Logger.debug("The service starts at {000} which lies within the time window", rn.getStartOfS());
				return 1;
			}else{
				Logger.debug("The service starts at {000} which does not lie within the time window", rn.getStartOfS());
				return 0;
			}
		case DROPOFF:
			Request associatedRd = rn.getAssociatedRequest();
			DropoffNode dropoff = associatedRd.getDropoff();
			SolutionRequest sR = rn.getSolutionRequest();
			RouteNode assPickup = sR.pickup;
			double startOfSpickup = assPickup.getStartOfS();
			
		Logger.debug("This is a dropoff node with time window: {00} - {00} and max ride time: {000}", dropoff.getE(), dropoff.getL(), associatedRd.L);
			if(rn.getStartOfS() >= dropoff.getE() && rn.getStartOfS() <= dropoff.getL() && rn.getStartOfS() - startOfSpickup < associatedRd.L){
				Logger.debug("Start service dropoff: {000}, start service pickup: {000}, ride time: {000}", rn.getStartOfS(),
						startOfSpickup, rn.getStartOfS() - startOfSpickup);
				return 1;
			}else{
				Logger.debug("Start service dropoff: {000}, start service pickup: {000}, ride time: {000}", rn.getStartOfS(),
						startOfSpickup, rn.getStartOfS() - startOfSpickup);
				return 0;
			}
		case DEPOT_END:
			Logger.debug("This is the ending depot, time window always satisfied");
			return 1;
		case TRANSFER_PICKUP:
			Logger.debug("This is a tranfer pickup node, it has no hard time window");
			return 1;
		case TRANSFER_DROPOFF:
			Logger.debug("This is a tranfer dropoff node, it has no hard time window");
			return 1;
		default:
			Logger.debug("This is a default node");
			return 1;
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
	
	
	// TODO some function to calculate feasible times given a route
	// TODO some function to check whether an insertion is valid?
}
