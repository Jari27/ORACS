/**
 * 
 */
package solution;

import org.pmw.tinylog.Logger;

import problem.Request;

/**
 * @author Jari Meevis
 *
 */
public class SolutionRequest {
	
	public Request associatedRequest;
	int id;
	int L;
	
	public RouteNode pickup, dropoff, transferPickup, transferDropoff;
	
	public SolutionRequest(Request associatedRequest) {
		this.associatedRequest = associatedRequest;
		this.id = associatedRequest.id;
		this.L = associatedRequest.L;
	}
	
	public RouteNode getPickup(){
		return this.pickup;
	}
	/**
	 * Checks whether this request is transferred somewhere.
	 * 
	 * @return true if the request is transferred, false otherwise.
	 */
	public boolean hasTransfer() {
		return transferPickup != null;
	}
	
}
