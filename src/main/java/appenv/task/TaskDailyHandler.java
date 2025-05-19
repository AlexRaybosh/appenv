package appenv.task;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class TaskDailyHandler extends TaskHandler {

	@Override
	public void init() {
		System.out.println("TaskHandler for "+getTaskType() +" inited with "+getConf());
	}


	@Override
	public void process(List<? extends TaskProcessingContext> tasks) throws InterruptedException {
		for (TaskProcessingContext c : tasks) {
			TaskRecord r = c.getTaskRecord();
			c.success(("processed #"+cnt.incrementAndGet()).getBytes());
		}
	}
	AtomicLong cnt=new AtomicLong();
}
