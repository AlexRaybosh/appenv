package appenv.task;

import java.util.List;

public class TaskDummyHandler extends TaskHandler {

	@Override
	public void init() {
		System.out.println("TaskHandler for "+getTaskType() +" inited with "+getConf());
	}


	@Override
	public void process(List<? extends TaskProcessingContext> tasks) throws Exception {
		for (TaskProcessingContext c : tasks) {
			System.out.println("Processing "+c.getTaskRecord());
			c.success("yes".getBytes());
			
		}
		
		
	}

}
