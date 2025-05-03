/*
 * Alex Raybosh, 2009-2015
 */

package appenv.async;

public interface CompletionCallback<T> {

	void completed(Workload workload, T ret, Object[] args);

	void errored(Workload workload, Throwable e, Object[] args);

}
