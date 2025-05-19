package appenv.task;

import java.util.concurrent.Future;

import appenv.db.StatementBlock;

public interface TaskProcessingContext {
	public TaskRecord getTaskRecord() throws InterruptedException;
	public Future<Void> success(byte[] result) throws InterruptedException;
	public Future<Void> error(String msg, Throwable e) throws InterruptedException;
	default Future<Void> error(Throwable e) throws InterruptedException {return error(null,e);}
	default Future<Void> error(String msg) throws InterruptedException {return error(msg,null);}
	public Future<Void> postpone(Long newTimeMs) throws InterruptedException;
	
	public void addTaskOnCommit(TaskRecord next);
	public void addStatementBlockOnCommit(StatementBlock<Void> block);
	boolean isProcessed();
}
