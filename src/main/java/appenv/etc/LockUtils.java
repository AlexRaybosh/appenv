package appenv.etc;

import java.sql.SQLException;
import java.util.concurrent.Callable;


import appenv.env.AppEnv;
import appenv.util.Utils;
import java.util.function.Consumer;

public class LockUtils {

	
	public static boolean tryExecuteWithEnvLock(final String lockName, Callable<Void> call) throws Exception {
		if (Utils.isEmpty(lockName) || Utils.isEmpty(AppEnv.envTypeName()) || AppEnv.systemProcessId()==null) {
			String msg="Can't perform unique lock operation for environment: "+AppEnv.envTypeName()+", lock: "+lockName+",  systemProcessId: "+AppEnv.systemProcessId();
			AppEnv.logerr(msg);
			throw new RuntimeException(msg);
		}
		final Long id = AppEnv.newId("unique_env_lock_id");
		boolean needCleanup=true;
		try {
			boolean acquired=acquire(id, lockName);
			if (!acquired) {
				cleanup(lockName);
				acquired=acquire(id, lockName);
			}
			if (!acquired) {
				needCleanup=false;
				return false;
			}
			call.call();
			return true;
		} catch (Throwable t) {
			Exception err = Utils.extractCause(t);
			throw err;
		} finally {
			if (needCleanup) cleanup(id);
		}
	}
	
	private static void cleanup(String lockName) throws SQLException, InterruptedException {
		AppEnv.db().update("delete from unique_env_lock l where l.lock_name=? and l.env_type=? and not exists (select 1 from system_process p where p.id=l.system_process_id and p.is_active=1)", true, lockName,AppEnv.envTypeName());
	}
	private static void cleanup(Long id) throws SQLException, InterruptedException {
		AppEnv.db().update("delete from unique_env_lock where id=?", true, id);
	}
	private static boolean acquire(Long id, String lockName) throws SQLException, InterruptedException {
		Long now=AppEnv.getTime();
		String sql="insert into unique_env_lock (id, lock_name, env_type, env_type_id, system_process_id, create_ms, last_ms) "
				+ "select t.* from (select ? as id,? as lock_name, ? as env_type, ? as env_type_id, ? as system_process_id, ? as create_ms, ? as last_ms) as t "
				+ "where not exists (select 1 from unique_env_lock l where l.lock_name=t.lock_name and l.env_type=t.env_type)";
		int cnt=AppEnv.db().update(sql, true, id, lockName, AppEnv.envTypeName(), AppEnv.envTypeId(), AppEnv.systemProcessId(), now, now);
		return cnt>0;
	}	
}
