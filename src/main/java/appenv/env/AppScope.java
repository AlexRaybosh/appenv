package appenv.env;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import com.google.gson.JsonObject;

import appenv.async.AsyncEngine;
import appenv.db.DB;
import appenv.env.boot.BootstrapAppConf;
import appenv.env.boot.Init;
import appenv.env.boot.InitSubSystems;
import appenv.env.boot.SubSystemStub;
import appenv.etc.DictionaryBase;
import appenv.etc.DictionaryWord;
import appenv.susbsystems.ProcessMaintenance;
import appenv.task.TaskQueueBackend;
import appenv.task.TaskQueueClient;
import appenv.util.DummyErrorFuture;
import appenv.util.DummyFuture;
import appenv.util.Utils;

public class AppScope {
	private volatile boolean isStopped=false;
	private AppConfOverride appConfOverride=null;

	public AppConfOverride getAppConfOverride() {return appConfOverride;}
	
	public String getPresetConfName() {
		return appConfOverride==null?null:appConfOverride.getPresetConfName();
	}
	public String getPresetBootstrapResource() {
		return appConfOverride==null?null:appConfOverride.getPresetBootstrapResource();
	}
	
	
	public final static String PROCESS_MAINTENANCE="processMaintenance";
	public final static String TASK_QUEUE_BACKEND="taskQueueBackend";
	public final static String TASK_QUEUE_CLIENT="taskQueueClient";
	

	public static AppScope createNonDefaultAppScope() {
		return createNonDefaultAppScope(null);
	}
	public static AppScope createNonDefaultAppScope(AppConfOverride appConfOverride) {
		AppScope appScope=new AppScope(appConfOverride);
		appScope.init();
		return appScope;
	}


	final static ScheduledExecutorService scheduledExecutorService=Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
		   @Override
		   public Thread newThread(Runnable r) {
		      Thread thread =  new Thread(r, "scheduler-thread");
		      thread.setDaemon(true);
		      return thread;
		   }
		});


	
	public static ScheduledExecutorService getScheduledExecutorService() {return scheduledExecutorService;}
	public static ExecutorService getExecutorService() {return AsyncEngine.getEngineExecutorService();}
	private AppScope(AppConfOverride appConfOverride) {
		this.appConfOverride=appConfOverride;


	}
	
	volatile Future<Init> initFuture;	
	volatile Map<String,Future<SubSystemStub>> subsystems=new ConcurrentHashMap<>(); 
	
	private void init() {
		initFuture=getExecutorService().submit(new Callable<Init>() {
			public Init call() throws Exception {
				return new Init(AppScope.this);
			}
		});
		final InitSubSystems subInit=new InitSubSystems(AppScope.this, subsystems, true);
		try {
			subInit.initialize();
			AsyncEngine.getEngineExecutorService().submit(()->{
				
				try {
					Init myInit = initFuture.get();
					myInit.derefAppSec();
					initFuture=new DummyFuture<>(myInit);
				} catch (Exception e) {
					BootstrapAppConf.logerr("AppScope initialization failed: ",e);
					initFuture=new DummyErrorFuture<>(e);
				}
				
				subsystems=subInit.resolve();
			});			
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	public final void reloadConfiguration() {
		Future<Init> newInitFuture = getExecutorService().submit(new Callable<Init>() {
			public Init call() throws Exception {
				return new Init(AppScope.this);
			}
		});
		try {
			Init newInit = newInitFuture.get();
			newInit.derefAppSec();
			Init oldInit = initFuture.get();
			initFuture=new DummyFuture<>(newInit);
			oldInit.destroy();
		} catch (Exception e) {
			AppEnv.logerr("AppScope reload configuration failed: ",Utils.extractCause(e));
			Utils.rethrowRuntimeException(e);
		}
	}
	
	public final void reloadSubSystems() {
		Map<String,Future<SubSystemStub>> newSubSystems=new HashMap<>();
		InitSubSystems newSubInit=new InitSubSystems(AppScope.this, newSubSystems, false);
		
		try {
			newSubInit.initialize();
			newSubSystems=newSubInit.resolve();
			Map<String, Future<SubSystemStub>> oldSubSystems = subsystems;
			subsystems=newSubSystems;
			for (Entry<String, Future<SubSystemStub>> e : oldSubSystems.entrySet()) {
				String name=e.getKey();
				Future<SubSystemStub> f = e.getValue();
				try {
					SubSystemStub s=f.get();
					if (s!=null) s.destroy();
				} catch (Exception ex) {
					AppEnv.logerr("AppScope subsystem reload: "+name+" cleanup error", ex);
				}
			}
			
		} catch (Exception e) {
			Utils.rethrowRuntimeException(e);
		}
	}
	
	
	/*
	 * should never be called from inside SubSystem::init ever! Will wait for itself....
	 */
	public final void ready() {
		try {
			if (initFuture!=null) initFuture.get();
		} catch (Exception e) {
			Utils.rethrowRuntimeException("AppScope core initialization error", e);
		}
		for (Entry<String, Future<SubSystemStub>> e : subsystems.entrySet()) {
			String name=e.getKey();
			Future<SubSystemStub> f = e.getValue();
			try {
				f.get();
			} catch (Exception ex) {
				Utils.rethrowRuntimeException("AppScope subsystem: "+name+" initialization error", ex);
			}
		}
	}

	public final boolean isStopped() {
		return isStopped;
	}
	public final void stop() {
		isStopped=true;
	}
	public final void destroy() {
		stop();
		try {
			initFuture.get().destroy();		
			for (Entry<String, Future<SubSystemStub>> e : subsystems.entrySet()) {
				String name=e.getKey();
				Future<SubSystemStub> f = e.getValue();
				try {
					SubSystemStub s=f.get();
					if (s!=null) {
						s.destroy();
					}
				} catch (Exception ex) {
					AppEnv.logerr("AppScope subsystem: "+name+" destruction error: ", ex);
				}
			}
						
			getScheduledExecutorService().shutdownNow();
			getExecutorService().shutdownNow();
			
		} catch (Exception e) {
			AppEnv.logerr("Failed to destroy AppScope", e);
		}
		
		
	}

	public final Properties getBootstrapProperties() {return getInit().getBootstrapProperties();}
	
	
	public final boolean hasDB() {return getInit().hasDB();}
	public final boolean hasDB(String dbName) {return getInit().hasDB(dbName);}
	public final DB getDB() {return getInit().getDB();}
	public final DB getDB(String dbName) {return getInit().getDB(dbName);}
	
	
	public final Long getSystemProcessId() {
		if (hasSubSystem(PROCESS_MAINTENANCE)) {
			ProcessMaintenance pm = this.<ProcessMaintenance>getSubSystem(PROCESS_MAINTENANCE);
			return pm==null?null:pm.getSystemProcessId();
		}
		return null;
	}

	public final TaskQueueBackend getTaskQueueBackend() {
		return this.<TaskQueueBackend>getSubSystem(TASK_QUEUE_BACKEND);
	}	
	public final TaskQueueClient getTaskQueueClient() {
		return this.<TaskQueueClient>getSubSystem(TASK_QUEUE_CLIENT);
	}	
	
	public final boolean hasSubSystem(String name) {
		return subsystems.containsKey(name);
	}
	
	public final <S> S getSubSystem(String name) {
		if (name==null) throw new RuntimeException("null subSystemName is not allowed");
		Future<SubSystemStub> stub = subsystems.get(name);
		try {
			if (stub!=null) {
				if (InitSubSystems.isSubInitThread()) {
					String callerName=InitSubSystems.getSubInitThreadSubName();
					Map<String, Set<String>> subDep = InitSubSystems.getSubInitThreadDependency();
					Set<String> callerDeps=subDep.get(callerName);
					if (name.equals(callerName)) {
						throw new RuntimeException("Subsystem \""+name+ "\" initialization deadlock: waiting on itself to initialize");	
					}
					// can only allow to proceed, if dependency was explicitly expressed
					if (!callerDeps.contains(name)) {
						throw new RuntimeException("Subsystem \""+callerName+ "\" initialization deadlock: waiting on \""+name+"\" to initialize without explicit dependency on it");
					}
				}				
				SubSystemStub s = stub.get();
				if (s!=null) return s.<S>getSubSystem();
			}
		} catch (Throwable ex) {
			return Utils.rethrowRuntimeException(ex);
		}
		throw new RuntimeException("Subsystem "+name+" is not registered or failed to initialize");
	}
	
	
	
	
	public final AppConf getAppConf() {return getInit().getAppConf();}
	public final Integer getAppConfId() {return getAppConf().getId();}
	
	
	public final Integer getClusterMemberId() {
		return hasSubSystem(PROCESS_MAINTENANCE)?this.<ProcessMaintenance>getSubSystem(PROCESS_MAINTENANCE).getClusterMemberId():null;		
	}


	public final Long newId(String name) throws SQLException, InterruptedException {
		try {
			return initFuture.get().getDBID().next(name);
		} catch (Exception e) {
			e=Utils.proceedUnlessSQLOrInterrupted(e);
			return Utils.rethrowRuntimeException(e);
		}
	}
	
	
	private Init getInit() {
		try {
			return initFuture.get();
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}
	}
	
	
	
	public final Set<String> getSubSystemNames() {return subsystems.keySet();}

	
	public final long getTime() {return System.currentTimeMillis();}

	volatile DictionaryBase dictionaryBase;
	public final DictionaryWord getDictionaryWord(Number id) {return getInit().getDictionaryWord(id);}	
	public final DictionaryWord getDictionaryWord(String word) {return getInit().getDictionaryWord(word);}	
	public final Future<Boolean> getHasWord(String word) {return getInit().hasWord(word);}
	public final Future<Boolean> getHasWord(Number id) {return getInit().hasWord(id);}
	public final boolean getWordCached(String word) {return getInit().wordCached(word);}
	public final boolean getWordCached(Number id) {return getInit().wordCached(id);}
	

	public final DictionaryWord getDictionaryWord(String base,Number id) {return getInit().getDictionaryWord(base,id);}	
	
	public final DictionaryWord getDictionaryWord(String base, String word) {return getInit().getDictionaryWord(base,word);}
	public final Future<Boolean> getHasWord(String base, String word) {return getInit().hasWord(base, word);}
	public final Future<Boolean> getHasWord(String base, Number id) {return getInit().hasWord(base, id);}
	public final boolean getWordCached(String base, String word) {return getInit().wordCached(base, word);}
	public final boolean getWordCached(String base, Number id) {return getInit().wordCached(base, id);}

	public final JsonObject getConfiguration() {return getAppConf().getConfiguration();}

	public final byte[] encryptAES(byte[] value, int off, int len) {return getInit().getAppSec().encryptAES(value, off, len);}
	public final byte[] decryptAES(byte[] value, int off, int len) {return getInit().getAppSec().decryptAES(value, off, len);}
	public final byte[] encryptAES(byte[] value) {return getInit().getAppSec().encryptAES(value);}
	public final byte[] decryptAES(byte[] value) {return getInit().getAppSec().decryptAES(value);}
	
	public final byte[] encryptPrivateRSA(byte[] value, int off, int len) {return getInit().getAppSec().encryptPrivateRSA(value, off, len);}	
	public final byte[] decryptPublicRSA(byte[] value, int off, int len) {return getInit().getAppSec().decryptPublicRSA(value, off, len);}
	public final byte[] signSHA256PrivateRSA(byte[] value, int off, int len) {return getInit().getAppSec().signSHA256PrivateRSA(value, off, len);}
	
	public final byte[] encryptPrivateRSA(byte[] value) {return getInit().getAppSec().encryptPrivateRSA(value);}	
	public final byte[] decryptPublicRSA(byte[] value) {return getInit().getAppSec().decryptPublicRSA(value);}
	public final byte[] signSHA256PrivateRSA(byte[] value) {return getInit().getAppSec().signSHA256PrivateRSA(value);}

	private volatile String version=readVersion();
	static String readVersion() {
		String ret=null;
		InputStream is =null;
		try {
			is=AppScope.class.getClassLoader().getResourceAsStream("/version");
			if (is==null) is=AppScope.class.getClassLoader().getResourceAsStream("version");
			if (is==null) return "not-versioned";
			ret=new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			ret="Failed to get version: "+e.getMessage();
		} finally {
			Utils.close(is);
		}
		ret=ret.replace("\n", "").replace("\r", "").trim();
		return ret;
	}
	public final String getAppVersion() {return version;}
	public final void setAppVersion(String version) throws SQLException, InterruptedException {
		this.version=version;
		if (hasSubSystem(PROCESS_MAINTENANCE)) {
			ProcessMaintenance pm=this.<ProcessMaintenance>getSubSystem(PROCESS_MAINTENANCE);
			if (pm!=null) pm.updateAppVersion(version);
		}	
	}
}
