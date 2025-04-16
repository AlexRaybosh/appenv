package testapp;

import appenv.db.DB;
import appenv.env.AppEnv;
import appenv.util.JsonUtils;

public class TestDb1 {

	public static void main(String[] args) throws Exception {
		System.out.println(AppEnv.envName());
		System.out.println(JsonUtils.prettyPrint(AppEnv.getConfiguration()));
		System.out.println(AppEnv.systemProcessId());
		//System.exit(0);
		AppEnv.ready();

		System.out.println(""+AppEnv.db().selectSingle("select now()"));
		AppEnv.destroy();
	}

}
