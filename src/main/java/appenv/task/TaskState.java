package appenv.task;

public enum TaskState {
	INIT(0), PROCESS(1), SUCCESS(2), ERROR(3), FATAL(4), CANCELED(5), INVALID(6);
	private final int stateId;
	private TaskState(int id) {
		this.stateId=id;
	}
	
	public final int getStateId() {return stateId;}
	public static TaskState id(int id) {
		switch (id) {
		case 0: return TaskState.INIT;
		case 1: return TaskState.PROCESS;
		case 2: return TaskState.SUCCESS;
		case 3: return TaskState.ERROR;
		case 4: return TaskState.FATAL;
		case 5: return TaskState.CANCELED;
		case 6: return TaskState.INVALID;
		default:
			throw new RuntimeException("Unknown TaskState id: "+id);
		}
	}
}
