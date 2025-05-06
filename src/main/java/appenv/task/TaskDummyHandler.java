package appenv.task;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import appenv.env.AppEnv;

public class TaskDummyHandler extends TaskHandler {

	@Override
	public void init() {
		System.out.println("TaskHandler for "+getTaskType() +" inited with "+getConf());
	}


	@Override
	public void process(List<? extends TaskProcessingContext> tasks) throws Exception {
		for (TaskProcessingContext c : tasks) {
			TaskRecord r = c.getTaskRecord();
			
			/*if (r.getErrorCount()==0) throw new RuntimeException("0 is runtime");
			if (r.getErrorCount()==1) {
				c.error("1 is error()");
				continue;
			}
			if (r.getErrorCount()==2) {
				try {
					throw new RuntimeException("2 is Im here exception");
				} catch (Exception e) {
					c.error(e);
				}
				continue;
			}*/
			//System.out.println("Processing "+c.getTaskRecord());
			c.success(("processed #"+cnt.incrementAndGet()).getBytes());
			//c.postpone(AppEnv.getTime());
			
		}	
	}
	AtomicLong cnt=new AtomicLong();

}
