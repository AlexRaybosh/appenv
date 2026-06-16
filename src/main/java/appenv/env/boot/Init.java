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
import appenv.env.AppConf;
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
	final AppConf appConf;
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
			BootstrapAppConf.logerr("AppScope security initialization failed: ",e);
			appSecFuture=new DummyErrorFuture<>(e);
		}
		
	}
	
	public Init(AppScope appScope) throws Exception {
		this.appScope=appScope;
		BootstrapAppConf bs=BootstrapAppConf.bootstrap(appScope);
		hasDB=bs.getDB()!=null;
		appConf=bs.getAppConf();
		appSecFuture=bs.getAppSecFuture();
		if (hasDB) {
			coreDB=bs.getDB();
			initDB("core",coreDB);
			dbid=new DBID(coreDB,"seq");
		} else {
			dbid=null;
			coreDB=null;
		}
		JsonObject allDBsConf = JsonUtils.getJsonObject(appConf.getConfiguration(), "database");

		if (null!=allDBsConf && !bs.isDBDisabled())for (Entry<String, JsonElement> e : allDBsConf.entrySet()) {
			String dbName=e.getKey();
			if ("core".equals(dbName)) continue;
			JsonObject dbConf = JsonUtils.getJsonObject(e.getValue());
			if (dbConf==null) continue;
			boolean optional=JsonUtils.getBoolean(false, dbConf, "optional");
			
			String dburl=JsonUtils.getString(dbConf,"properties", "dburl");
			if (dburl==null) {
				if (!optional) BootstrapAppConf.logerr("Skipping DB "+dbName+", no dburl available");
				continue;
			}
			String dbuser=JsonUtils.getString(dbConf,"properties", "dbuser");
			String dbpassword=JsonUtils.getString(dbConf,"properties", "dbpassword");
			String dbpasswordBootstrapPropertyName=JsonUtils.getString(dbConf,"properties", "dbpasswordBootstrapPropertyName");
			
			if (dbpasswordBootstrapPropertyName!=null && bs.getProperties().containsKey(dbpasswordBootstrapPropertyName)) {
				dbpassword=bs.getProperties().getProperty(dbpasswordBootstrapPropertyName);
			}
			DB db=DB.create(dburl, dbuser, dbpassword);
			db=BootstrapAppConf.reinit(db, appConf.getConfiguration(), dbName, dburl, dbuser, dbpassword);
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
		JsonObject dbConf = JsonUtils.getJsonObject(appConf.getConfiguration(),"database", name);
		int maxCachedPreparedStatements=db.getConfDialectIntProperty(50, dbConf, "maxCachedPreparedStatements");
		//int maxCachedPreparedStatements=JsonUtils.getInteger(50, appConf.getConfiguration(),"database", name, "maxCachedPreparedStatements");
		db.setMaxCachedPreparedStatements(maxCachedPreparedStatements);
		
		int maxConnections=db.getConfDialectIntProperty(20, dbConf, "maxConnections");
		//int maxConnections=JsonUtils.getInteger(5, appConf.getConfiguration(), "database", name, "maxConnections");
		db.setMaxConnections(maxConnections);
		
		long retryTimeoutMilliseconds=(long)(1000*db.getConfDialectNumberProperty(10, dbConf,  "retryTimeoutSeconds").doubleValue());
		db.setRetryTimeout(TimeUnit.MILLISECONDS, retryTimeoutMilliseconds);
		
		int transactionIsolation=db.getConfDialectIntProperty(Connection.TRANSACTION_READ_COMMITTED, dbConf, "transactionIsolation");
		db.setTransactionIsolation(transactionIsolation);
		
		int batchSize=db.getConfDialectIntProperty(256, dbConf, "batchSize");
		db.setBatchSize(batchSize);
		
		long overborrowPenaltyTimeoutMilliseconds=(long)(1000*db.getConfDialectNumberProperty(0.1, dbConf,  "overborrowPenaltySeconds").doubleValue());
		db.setOverborrowPenaltyTimeout(TimeUnit.MILLISECONDS, overborrowPenaltyTimeoutMilliseconds);
		
		for (JsonElement e : JsonUtils.getJsonArrayIterable(db.getConfDialectJsonElement(dbConf, "initStatements"))) {
			String onOpen=JsonUtils.getString(e, "onOpen");
			String onClose=JsonUtils.getString(e, "onClose");
			Boolean autoCommit=JsonUtils.getBoolean(e, "autoCommit");
			if (autoCommit!=null) db.addInitSqlWithCleanup(autoCommit, onOpen, onClose);
			else {
				db.addInitSqlWithCleanup(true, onOpen, onClose);
				db.addInitSqlWithCleanup(false, onOpen, onClose);
			}
		}
		
		boolean allowOverborrow=db.getConfDialectBooleanProperty(true, dbConf, "allowOverborrow");
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
	public final AppConf getAppConf() {
		return appConf;
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
