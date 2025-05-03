package testapp;


import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import appenv.env.AppEnv;
import appenv.task.TaskFuture;
import appenv.task.TaskQueueBackend;
import appenv.task.TaskQueueClient;
import appenv.task.TaskType;
import appenv.util.JsonUtils;


public class TestTaskServerPostgres {

	public static void main(String[] strs) throws Exception {
		AppEnv.presetBootstrapResource("bootstrap-postgres.json");
		AppEnv.presetAppConfName("test-task-server");
		System.out.println(AppEnv.confName());
		System.out.println(JsonUtils.prettyPrint(AppEnv.conf()));
		
		TaskQueueBackend taskQueueServer = AppEnv.taskQueueBackend();

		
		
		
		Thread.sleep(10000000);
		AppEnv.destroy();
		//AppScope.getAppScope().destroy();
		
	}

}
