package testapp;


import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import appenv.env.AppEnv;
import appenv.task.TaskFuture;
import appenv.task.TaskQueueClient;
import appenv.task.TaskType;
import appenv.util.JsonUtils;


public class TestTaskClient {

	public static void main(String[] strs) throws Exception {
		AppEnv.presetEnvName("dev");
		System.out.println(AppEnv.envName());
		System.out.println(JsonUtils.prettyPrint(AppEnv.getConfiguration()));
		
		TaskQueueClient taskQueueClient = AppEnv.taskQueueClient();

		String ticket=null;
		
		byte[] payload="hello world".getBytes();
		TaskType tt=TaskType.name("dummy");
		List<TaskFuture> submits=new ArrayList<>();
		long s=System.currentTimeMillis();
		int N=1000000;
		for (int i=0; i< N; ++i) {
			JsonObject props=JsonUtils.parseJsonObject("{\"field1\": "+i+", \"field2\" : [\"hello"+i+"\"]}");
			TaskFuture f=taskQueueClient.submit(tt, ticket, payload, props );
			submits.add(f);
		}
		for (TaskFuture f : submits) {
			f.get();	
		}
		long e=System.currentTimeMillis();
		double sec=(e-s)/1000.0;
		double r=(N/sec);
		System.out.println("rate "+r+" tasks/sec");
		/*for (TaskFuture f : submits) {
			System.out.print(f.getTicket());
			System.out.println(" - "+f.get());
		}*/
		
		
		
		Thread.sleep(10000);
		AppEnv.destroy();
		//AppScope.getAppScope().destroy();
		
	}

}
