package testapp;


import appenv.env.AppEnv;
import appenv.etc.DictionaryWord;
import appenv.task.TaskQueue;
import appenv.util.JsonUtils;


public class TestTask1 {

	public static void main(String[] strs) throws Exception {
		AppEnv.presetEnvName("test4");
		System.out.println(AppEnv.envName());
		System.out.println(JsonUtils.prettyPrint(AppEnv.getConfiguration()));
		
		TaskQueue taskQueue = AppEnv.taskQueue();

		
		Thread.sleep(10000);
		AppEnv.destroy();
		//AppScope.getAppScope().destroy();
		
	}

}
