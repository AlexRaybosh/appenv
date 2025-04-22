package testapp;

import appenv.env.AppEnv;
import appenv.util.JsonUtils;

public class TestDb1 {

	public static void main(String[] args) throws Exception {
		System.out.println(AppEnv.confName());
		System.out.println(JsonUtils.prettyPrint(AppEnv.conf()));
		System.out.println(AppEnv.systemProcessId());
		//System.exit(0);
		AppEnv.ready();

		System.out.println(""+AppEnv.db().selectSingle("select now()"));
		AppEnv.destroy();
	}

}
