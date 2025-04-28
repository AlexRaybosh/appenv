package appenv.task;

public class TaskDailyHandler extends TaskHandler {

	@Override
	public void init() {
		System.out.println("TaskHandler for "+getTaskType() +" inited with "+getConf());
	}

}
