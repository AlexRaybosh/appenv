/*
 * Alex Raybosh, 2009-2015
 */

package appenv.async;

public interface Service<T> {
	Result<T> call(Workload w, Object... args) throws InterruptedException;
	Result<T> callNoLimit(Workload w, Object... args) throws InterruptedException;
	
	void callWithCallback(Workload w, CompletionCallback<T> callback,Object... args) throws InterruptedException;
	void callWithCallbackNoLimit(Workload w, CompletionCallback<T> callback,Object... args) throws InterruptedException;
	
	void callWithCallback(CompletionCallback<T> callback,Object... args) throws InterruptedException;
	void callWithCallbackNoLimit(CompletionCallback<T> callback,Object... args) throws InterruptedException;
	Workload getTrackingWorkload();

	boolean tryCallWithCallback(CompletionCallback<T> callback, Object... args) throws InterruptedException;
	boolean tryCallWithCallback(Workload w, CompletionCallback<T> callback, Object... args) throws InterruptedException;

}
