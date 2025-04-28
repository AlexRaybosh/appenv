package appenv.task;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import appenv.env.AppEnv;

public class TaskFuture implements Future<Void> {
	final String ticket;
	final Future<Void> future;
	TaskFuture(Future<Void> future) {this(null, future);}
	public TaskFuture(String ticket, Future<Void> future) {
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
	public Void get() throws InterruptedException, ExecutionException {return future.get();}

	@Override
	public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {return future.get(timeout, unit);}

}
