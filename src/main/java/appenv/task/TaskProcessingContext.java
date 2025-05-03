package appenv.task;

import appenv.db.StatementBlock;

public interface TaskProcessingContext {
	public TaskRecord getTaskRecord() throws InterruptedException;
	public void success(byte[] result);
	public void error(String msg, Throwable e);
	default void error(Throwable e) {error(null,e);}
	default void error(String msg) {error(msg,null);}
	public void postpone(Long newTimeMs);
	
	public void addTaskOnCommit(TaskRecord next);
	public void addStatementBlockOnCommit(StatementBlock<Void> block);
}
