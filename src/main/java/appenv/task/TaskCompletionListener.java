package appenv.task;

public interface TaskCompletionListener {
	public void onTaskCompleted(TaskRecord taskRecord, byte[] result);
}
