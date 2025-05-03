/*
 * Alex Raybosh, 2009-2015
 */

package appenv.async;

import java.util.concurrent.Future;

public interface Workload {
	public <T> Result<T> call(String serviceName, Object... args) throws InterruptedException;
	public <T> void callWithCallback(String serviceName, CompletionCallback<T> callback, Object... args) throws InterruptedException;
	public <T> Result<T> callNoLimit(String serviceName, Object... args) throws InterruptedException;
	public <T> void callWithCallbackNoLimit(String serviceName, CompletionCallback<T> callback, Object... args) throws InterruptedException;

	
	/**
	 * Signals the last call to the workload. See also: complete() can be called to collect completion future 
	 * @return Number of submitted calls 
	 */
	long last();
	/**
	 * Resets counter of calls, so done()/complete() can be called again
	 */
	void reset();
	
	/**
	 * @return Number of submitted calls 
	 */
	public long getNumberOfSubmittedCalls();
	/**
	 * @return Number of submitted calls 
	 */
	long getNumberOfCompletedCalls();
	
	/**
	 * @return Future<Long> of completed number of calls, once <code>last()</code> is called on the Workload
	 */
	Future<Long> completeLast();
	
	void callSubmitted();
	void callCompleted();
	

	
}
