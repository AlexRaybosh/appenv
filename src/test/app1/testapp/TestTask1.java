package testapp;


import appenv.env.AppEnv;
import appenv.etc.DictionaryWord;
import appenv.util.JsonUtils;


public class TestTask1 {

	public static void main(String[] strs) throws Exception {
		AppEnv.presetEnvName("test3");
		System.out.println(AppEnv.envName());
		System.out.println(JsonUtils.prettyPrint(AppEnv.getConfiguration()));
		System.out.println(AppEnv.systemProcessId());
		//System.exit(0);
		AppEnv.ready();
		
		//AppEnv.logerr("test error", new RuntimeException("zzz"));
		
		
		
		DictionaryWord dw=AppEnv.word("three");
		System.out.println(dw.getWord());
		
		Thread.sleep(1000);
		AppEnv.reloadEnvironment();
		
		Thread.sleep(1000);
		AppEnv.reloadSubSystems();
		
		Thread.sleep(10000);
		AppEnv.destroy();
		//AppScope.getAppScope().destroy();
		
	}

}
