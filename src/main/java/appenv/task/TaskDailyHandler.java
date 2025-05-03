package appenv.task;

import java.util.Collection;
import java.util.List;

public class TaskDailyHandler extends TaskHandler {

	@Override
	public void init() {
		System.out.println("TaskHandler for "+getTaskType() +" inited with "+getConf());
	}


	@Override
	public void process(List<? extends TaskProcessingContext> pl) {
		// TODO Auto-generated method stub
		
	}

}
