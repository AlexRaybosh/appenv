package testapp;

import appenv.env.AppEnv;
import appenv.etc.DictionaryWord;
import appenv.util.JsonUtils;


public class TestApp1 {

	public static void main(String[] strs) throws Exception {
		AppEnv.presetAppConfName("testapp1");
		System.out.println(AppEnv.envTypeName() + " - " + AppEnv.envTypeId());
		System.out.println(AppEnv.confName() + " - "+AppEnv.confId());
		System.out.println(JsonUtils.prettyPrint(AppEnv.conf()));
		System.out.println(AppEnv.systemProcessId());
		//System.exit(0);
		AppEnv.ready();
		
		//AppEnv.logerr("test error", new RuntimeException("zzz"));
		
		
		
		DictionaryWord dw=AppEnv.word("four");
		System.out.println(dw.getWord()+ " - " + dw.getId());
		
		Thread.sleep(1000);
		AppEnv.reloadConfiguration();
		
		Thread.sleep(1000);
		AppEnv.reloadSubSystems();
		
		Thread.sleep(10000);
		AppEnv.destroy();
		//AppScope.getAppScope().destroy();
		
	}

}
