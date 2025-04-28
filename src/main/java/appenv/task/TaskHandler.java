package appenv.task;

import com.google.gson.JsonObject;

public abstract class TaskHandler {

	private JsonObject handlerConf;
	private TaskType taskType;

	public void init(TaskType taskType, JsonObject handlerConf) throws Exception {
		this.handlerConf=handlerConf;
		this.taskType=taskType;
		init();
	}
	public abstract void init() throws Exception;
	
	public final JsonObject getConf() {
		return handlerConf;
	}
	public final TaskType getTaskType() {
		return taskType;
	}
	
}
