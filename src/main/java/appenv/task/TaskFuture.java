package appenv.task;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import appenv.env.AppEnv;

public class TaskFuture implements Future<Long> {
	final String ticket;
	final Future<Long> future;
	TaskFuture(Future<Long> future) {this(null, future);}
	public TaskFuture(String ticket, Future<Long> future) {
		this.ticket=ticket==null?AppEnv.createUniqueKey():ticket;
		this.future=future;
	}
	public final String getTicket() {return ticket;}
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {return future.cancel(mayInterruptIfRunning);}

	@Override
	public boolean isCancelled() {return future.isCancelled();}

	@Override
	public boolean isDone() {return future.isDone();}

	@Override
	public Long get() throws InterruptedException, ExecutionException {return future.get();}

	@Override
	public Long get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {return future.get(timeout, unit);}

}
