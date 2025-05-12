package appenv.task;

public interface TaskQueueBackend {
	public void addTaskCompletionListener(TaskCompletionListener lst);
}
