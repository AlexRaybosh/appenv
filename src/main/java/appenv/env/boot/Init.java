package appenv.env.boot;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import appenv.db.DB;
import appenv.db.DBID;
import appenv.env.AppScope;
import appenv.env.Env;
import appenv.etc.DictionaryBase;
import appenv.etc.DictionaryWord;
import appenv.util.DummyErrorFuture;
import appenv.util.DummyFuture;
import appenv.util.JsonUtils;
import appenv.util.Utils;

public class Init {
	

	final DBID dbid;
	volatile Future<AppSec> appSecFuture;
	//ClusterMember clusterMember;
	final DB coreDB;
	final Map<String,DB> sideDBs=new HashMap<>();
	final Env env;
	final boolean hasDB;
	final AppScope appScope;
	final Properties bootstrapProperties;
	
	public final AppSec getAppSec() {
		try {
			return appSecFuture.get();
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}
	}
	
	public void derefAppSec()  {
		try {
			appSecFuture=new DummyFuture<AppSec>(appSecFuture.get());
		} catch (Exception e) {
			BootstrapEnv.logerr("AppScope security initialization failed: ",e);
			appSecFuture=new DummyErrorFuture<>(e);
		}
		
	}
	
	public Init(AppScope appScope) throws Exception {
		this.appScope=appScope;
		BootstrapEnv bs=BootstrapEnv.bootstrap(appScope);
		hasDB=bs.getDB()!=null;
		env=bs.getEnv();
		appSecFuture=bs.getAppSecFuture();
		if (hasDB) {
			coreDB=bs.getDB();
			initDB("core",coreDB);
			dbid=new DBID(coreDB,"seq");
		} else {
			dbid=null;
			coreDB=null;
		}
		JsonObject allDBsConf = JsonUtils.getJsonObject(env.getConfiguration(), "database");

		if (null!=allDBsConf && !bs.isDBDisabled())for (Entry<String, JsonElement> e : allDBsConf.entrySet()) {
			String dbName=e.getKey();
			if ("core".equals(dbName)) continue;
			JsonObject dbConf = JsonUtils.getJsonObject(e.getValue());
			if (dbConf==null) continue;
			String dburl=JsonUtils.getString(dbConf,"properties", "dburl");
			if (dburl==null) {
				BootstrapEnv.logerr("Skipping DB "+dbName+", no dburl available");
				continue;
			}
			String dbuser=JsonUtils.getString(dbConf,"properties", "dbuser");
			String dbpassword=JsonUtils.getString(dbConf,"properties", "dbpassword");
			String dbpasswordBootstrapPropertyName=JsonUtils.getString(dbConf,"properties", "dbpasswordBootstrapPropertyName");
			
			if (dbpasswordBootstrapPropertyName!=null && bs.getProperties().containsKey(dbpasswordBootstrapPropertyName)) {
				dbpassword=bs.getProperties().getProperty(dbpasswordBootstrapPropertyName);
			}
			DB db=DB.create(dburl, dbuser, dbpassword);
			db=BootstrapEnv.reinit(db, env.getConfiguration(), dbName, dburl, dbuser, dbpassword);
			initDB(dbName, db);
			sideDBs.put(dbName, db);
		}
		if (coreDB!=null) {
			sideDBs.put("core", coreDB);
		}
		this.bootstrapProperties=bs.getProperties();
	}
	public final boolean hasDB() {			
		return hasDB;
	}

	private void initDB(String name, DB db) {
		int maxCachedPreparedStatements=JsonUtils.getInteger(50, env.getConfiguration(),"database", name, "maxCachedPreparedStatements");
		db.setMaxCachedPreparedStatements(maxCachedPreparedStatements);
		int maxConnections=JsonUtils.getInteger(5, env.getConfiguration(), "database", name, "maxConnections");
		db.setMaxConnections(maxConnections);
		long retryTimeoutMilliseconds=JsonUtils.getLong(20000L,env.getConfiguration(),"database", name,  "retryTimeoutMilliseconds");
		db.setRetryTimeout(TimeUnit.MILLISECONDS, retryTimeoutMilliseconds);
		int transactionIsolation=JsonUtils.getInteger(Connection.TRANSACTION_READ_COMMITTED, env.getConfiguration(), "database", name, "transactionIsolation");
		db.setTransactionIsolation(transactionIsolation);
		
		int batchSize=JsonUtils.getInteger(128, env.getConfiguration(), "database", name, "batchSize");
		db.setBatchSize(batchSize);
		
		long overborrowPenaltyTimeoutMilliseconds=JsonUtils.getLong(200L,env.getConfiguration(),"database", name,  "overborrowPenaltyTimeoutMilliseconds");
		db.setOverborrowPenaltyTimeout(TimeUnit.MILLISECONDS, overborrowPenaltyTimeoutMilliseconds);
		for (JsonElement e : JsonUtils.getJsonArrayIterable(env.getConfiguration(), "database", name, "initStatements")) {
			String onOpen=JsonUtils.getString(e, "onOpen");
			String onClose=JsonUtils.getString(e, "onClose");
			boolean autoCommit=JsonUtils.getBool(e, "autoCommit");
			db.addInitSqlWithCleanup(autoCommit, onOpen, onClose);
		}
		boolean allowOverborrow=JsonUtils.getBoolean(true,env.getConfiguration(), "database", name, "allowOverborrow");
		db.allowOverborrow(allowOverborrow);
	}


	public final Properties getBootstrapProperties() {return bootstrapProperties;}
	

	public final DB getDB() {
		if (!hasDB()) throw new RuntimeException("core DB is not available");
		return coreDB;
	}
	public final DB getDB(String name) {
		if (!sideDBs.containsKey(name)) {
			if (name==null) name="null";
			throw new RuntimeException(name+" DB is not available");
		}
		return sideDBs.get(name);
	}
	public final boolean hasDB(String name) {			
		return sideDBs.get(name)!=null;
	}

	
	public void destroy() {
		for (DB db : sideDBs.values()) {
			if (db!=null) db.close();
		}
	}
	public final Env getEnv() {
		return env;
	}
	public final DBID getDBID() {
		return dbid;
	}
	public final DictionaryWord getDictionaryWord(String word) {return getDefaultDictionaryBase().word(word);}
	public final DictionaryWord getDictionaryWord(Number id) {return getDefaultDictionaryBase().word(id);}
	Object lock=new Object(); 
	volatile DictionaryBase defaultDictionaryBase;
	Map<String,DictionaryBase> dictionaryBaseMap;
	
	public final DictionaryBase getDefaultDictionaryBase() {
		DictionaryBase ret=defaultDictionaryBase;
		if (ret!=null) return ret;
		synchronized (lock) {
			if (defaultDictionaryBase==null) {
				if (!hasDB()) throw new RuntimeException("core DB is not available");
				defaultDictionaryBase=new DictionaryBase(appScope);
			}
			return defaultDictionaryBase;
		}
	}
	private DictionaryBase getDictionaryBase(String base) {
		if (base==null) return getDefaultDictionaryBase();
		synchronized (lock) {
			if (dictionaryBaseMap==null) dictionaryBaseMap=new HashMap<>();
			DictionaryBase dict=dictionaryBaseMap.get(base);
			if (dict==null) {
				if (!hasDB()) throw new RuntimeException("core DB is not available");
				dict=new DictionaryBase(appScope, base);
			}
			return dict;
		}
	}
	
	public Future<Boolean> hasWord(String word) {
		return getDefaultDictionaryBase().has(word);
	}
	public Future<Boolean> hasWord(Number id) {
		return getDefaultDictionaryBase().has(id);
	}
	public boolean wordCached(String word) {
		return getDefaultDictionaryBase().cached(word);
	}
	public boolean wordCached(Number id) {
		return getDefaultDictionaryBase().cached(id);
	}
	public DictionaryWord getDictionaryWord(String base, String word) {
		return getDictionaryBase(base).word(word); 
	}
	public DictionaryWord getDictionaryWord(String base, Number id) {
		return getDictionaryBase(base).word(id);
	}
	public Future<Boolean> hasWord(String base, String word) {
		return getDictionaryBase(base).has(word);
	}
	public Future<Boolean> hasWord(String base, Number id) {
		return getDictionaryBase(base).has(id);
	}
	public boolean wordCached(String base, String word) {
		return getDictionaryBase(base).cached(word);
	}
	public boolean wordCached(String base, Number id) {
		return getDictionaryBase(base).cached(id);
	}

}
