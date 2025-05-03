/*
 * Alex Raybosh, 2009-2015
 */

package appenv.async;


public class Request<T> {

	final private Workload workload;
	final private CompletionCallback<T> callback;
	final private Object[] args;
	private boolean reported;

	public Request(Workload workload, CompletionCallback<T> callback, Object[] args) {
		this.workload=workload;
		this.callback=callback;
		this.args=args;
	}

	public final void errored(Throwable e) {
		if (!reported) {
			reported=true;
			try {
				callback.errored(workload, e, args );
			} finally {
				workload.callCompleted();
			}
		}		
	}

	public final void completed() {
		if (!reported) {
			reported=true;
			try {
				callback.completed(workload, null, args );
			} finally {
				workload.callCompleted();
			}
		}
	}

	public final Object[] getArgs() {
		return args;
	}
	@SuppressWarnings("unchecked")
	public final <A>  A arg(int i) {
		return (A)args[i];
	}


	public final void setResult(T result) {
		if (!reported) {
			reported=true;
			try {
				callback.completed(workload, result, args );
			} finally {
				workload.callCompleted();
			}
		}
	}
}
