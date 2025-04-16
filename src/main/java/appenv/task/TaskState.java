package appenv.task;

public enum TaskState {
	INIT(0), PROCESS(1), SUCCESS(2), ERROR(3), FATAL(4);
	private final int stateId;
	private TaskState(int id) {
		this.stateId=id;
	}
	public final int getStateId() {return stateId;}
}
