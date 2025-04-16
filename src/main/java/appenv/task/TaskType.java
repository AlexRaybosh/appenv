package appenv.task;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import appenv.async.AsyncEngine;
import appenv.env.AppEnv;
import appenv.util.DummyFuture;
import appenv.util.JsonUtils;
import appenv.util.Utils;

public class TaskType {
	final Future<TaskType> future;
	final String name;
	final Integer id;

	public Integer getId() {
		if (id != null) return id;
		try {
			return future.get().getId();
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}
	}

	public String getName() {
		if (name != null) return name;
		try {
			return future.get().getName();
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}
	}

	public TaskType(String name) {
		this.name=name;
		this.id=null;
		future = getFutureByName(name);
	}
	
	public TaskType(Integer id) {
		this.name=null;
		this.id=id;
		future = getFutureById(id);
	}	
	private static Future<TaskType> getFutureByName(String name) {
		try {return typeNameCache.get(name);} catch (Exception e) {return Utils.rethrowRuntimeException(e);}
	}
	private static Future<TaskType> getFutureById(Integer id) {
		try {return typeIdCache.get(id);} catch (Exception e) {return Utils.rethrowRuntimeException(e);}		
	}
	
	private TaskType(Integer id, String name) {
		this.name=name;
		this.id=id;
		this.future=null;
	}

	@Override
	public String toString() {
		return "TaskType [id=" + getId() + ", name='" + getName() +"']";
	}

	static int intConf(int fallBack, String param) {
		return JsonUtils.getInteger(fallBack, AppEnv.configuration(), param);
	}

	static LoadingCache<String, Future<TaskType>> typeNameCache = CacheBuilder.newBuilder()
			.expireAfterAccess(intConf(30, "taskTypeCacheSeconds"), TimeUnit.SECONDS)
			.expireAfterWrite(intConf(30, "taskTypeCacheSeconds"), TimeUnit.SECONDS)
			.maximumSize(intConf(256, "taskTypeCacheSize")).build(new CacheLoader<String, Future<TaskType>>() {
				public Future<TaskType> load(final String name) throws Exception {
					return AsyncEngine.getEngineExecutorService().submit(new Callable<TaskType>() {
						public TaskType call() throws Exception {
							return loadByName(name);
						}
					});
				}

			});

	static LoadingCache<Integer, Future<TaskType>> typeIdCache = CacheBuilder.newBuilder()
			.expireAfterAccess(intConf(30, "taskTypeCacheSeconds"), TimeUnit.SECONDS)
			.expireAfterWrite(intConf(30, "taskTypeCacheSeconds"), TimeUnit.SECONDS)
			.maximumSize(intConf(256, "taskTypeCacheSize")).build(new CacheLoader<Integer, Future<TaskType>>() {
				public Future<TaskType> load(final Integer id) throws Exception {
					return AsyncEngine.getEngineExecutorService().submit(new Callable<TaskType>() {
						public TaskType call() throws Exception {
							return loadById(id);
						}
					});
				}

			});

	final static String dbPoolName=initDbPoolName();
	private static String initDbPoolName() {
		return JsonUtils.getString("core", AppEnv.configuration(), "taskDbPoolName");
	}
	
	private static TaskType loadById(Integer id) throws SQLException, InterruptedException {
		String name=AppEnv.db(dbPoolName).<String>selectSingle("select task_type_name from task_type where id=?",false,id);
		if (name==null) throw new RuntimeException("task_type with id: "+id+" not found");
		TaskType tt = new TaskType(id, name);
		typeNameCache.put(name, new DummyFuture<TaskType>(tt));
		return tt;
	}

	

	private static TaskType loadByName(String name) throws Exception {
		Number id=AppEnv.db(dbPoolName).<Number>selectSingle("select id from task_type where task_type_name=?",false,name);
		if (id==null) throw new RuntimeException("task_type with name: "+name+" not found");
		TaskType tt = new TaskType(id.intValue(), name);
		typeIdCache.put(id.intValue(), new DummyFuture<TaskType>(tt));
		return tt;

	}

	public static void main(String[] args) throws Exception {
		TaskType tt=new TaskType("dummy");
		TaskType tt1=new TaskType(1);
		System.out.println(tt);
		System.out.println(tt1);

	}

}
