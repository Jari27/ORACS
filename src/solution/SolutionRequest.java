/**
 * 
 */
package solution;

import problem.Request;

/**
 * @author Jari Meevis
 *
 */
public class SolutionRequest {
	
	Request associatedRequest;
	int id;
	
	RouteNode pickup, dropoff, transferPickup, transferDropoff;
	
	public SolutionRequest(Request associatedRequest) {
		this.associatedRequest = associatedRequest;
		this.id = associatedRequest.id;
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
