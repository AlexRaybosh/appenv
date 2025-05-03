/*
 * Alex Raybosh, 2009-2015
 */

package appenv.async;

import java.util.List;

public abstract class ServiceBackend<T> {

	public abstract void process(List<Request<T>> bulk) throws Exception;
	public int getMaxBulkSize() {return 1;}
	public long getWorkerReleaseTimeout() {return 0;}
	public int getMaxWorkers() {return 1;}
	public int getMaxQueuedRequests() {return 1;}

}
