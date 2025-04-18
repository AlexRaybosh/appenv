package appenv.task;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonElement;

import appenv.async.AsyncEngine;
import appenv.env.AppEnv;
import appenv.util.DummyFuture;
import appenv.util.JsonUtils;
import appenv.util.Utils;

public class TaskType {
	final Future<TaskType> future;
	final String name;
	final Integer id;
	final JsonElement meta;

	public Integer getId() {
		try {
			if (future!=null) return future.get().getId();
			return id;
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}
	}

	public String getName() {
		try {
			if (future!=null) return future.get().getName();
			return name;
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}		
	}
	public JsonElement getMeta() {
		try {
			if (future!=null) return future.get().getMeta();
			return meta;
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}		
	}

	public static TaskType name(String name) {
		return new TaskType(name);
	}
	public static TaskType id(int id) {
		return new TaskType(id);
	}
	
	public TaskType(String name) {
		this.name=name;
		this.id=null;
		this.meta=null;
		future = getFutureByName(name);
	}
	
	public TaskType(Integer id) {
		this.name=null;
		this.id=id;
		this.meta=null;
		future = getFutureById(id);
	}	
	private static Future<TaskType> getFutureByName(String name) {
		try {return typeNameCache.get(name);} catch (Exception e) {return Utils.rethrowRuntimeException(e);}
	}
	private static Future<TaskType> getFutureById(Integer id) {
		try {return typeIdCache.get(id);} catch (Exception e) {return Utils.rethrowRuntimeException(e);}		
	}
	
	private TaskType(Integer id, String name, String metaStr) {
		this.name=name;
		this.id=id;
		meta=parse(metaStr);
		this.future=null;
	}

	private static JsonElement parse(String metaStr) {
		if (metaStr==null) return null;
		return JsonUtils.parseJsonElement(metaStr, true);
	}

	@Override
	public String toString() {
		return "TaskType [id=" + getId() + ", name='" + getName() +"', meta='"+getMeta()+"']";
	}

	static int intConf(int fallBack, String... params) {
		int ret=JsonUtils.getInteger(fallBack, AppEnv.configuration(), params);
		return ret;
	}

	static LoadingCache<String, Future<TaskType>> typeNameCache = CacheBuilder.newBuilder()
			.expireAfterAccess(intConf(30, "task", "typeCacheSeconds"), TimeUnit.SECONDS)
			.expireAfterWrite(intConf(30, "task", "typeCacheSeconds"), TimeUnit.SECONDS)
			.maximumSize(intConf(256, "task", "typeCacheSeconds")).build(new CacheLoader<String, Future<TaskType>>() {
				public Future<TaskType> load(final String name) throws Exception {
					return AsyncEngine.getEngineExecutorService().submit(new Callable<TaskType>() {
						public TaskType call() throws Exception {
							return loadByName(name);
						}
					});
				}

			});

	static LoadingCache<Integer, Future<TaskType>> typeIdCache = CacheBuilder.newBuilder()
			.expireAfterAccess(intConf(30, "task", "typeCacheSeconds"), TimeUnit.SECONDS)
			.expireAfterWrite(intConf(30, "task", "typeCacheSeconds"), TimeUnit.SECONDS)
			.maximumSize(intConf(256, "task", "typeCacheSize")).build(new CacheLoader<Integer, Future<TaskType>>() {
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
		return JsonUtils.getString("core", AppEnv.configuration(), "task", "database");
	}
	
	private static TaskType loadById(Integer id) throws SQLException, InterruptedException {
		Object[] row=AppEnv.db(dbPoolName).selectFirstRow("select task_type_name, meta from task_type where id=?",false,id);
		if (row==null) throw new RuntimeException("task_type with id: "+id+" not found");
		String name=(String)row[0];
		String metaStr=(String)row[1];
		TaskType tt = new TaskType(id, name, metaStr);
		typeNameCache.put(name, new DummyFuture<TaskType>(tt));
		return tt;
	}

	

	private static TaskType loadByName(String name) throws Exception {
		Object[] row=AppEnv.db(dbPoolName).selectFirstRow("select id, meta from task_type where task_type_name=?",false,name);
		if (row==null) throw new RuntimeException("task_type with name: "+name+" not found");
		int id=((Number)row[0]).intValue();
		String metaStr=(String)row[1];
		TaskType tt = new TaskType(id, name,metaStr);
		typeIdCache.put(id, new DummyFuture<TaskType>(tt));
		return tt;

	}

	public static void main(String[] args) throws Exception {
		TaskType tt=TaskType.name("dummy");
		TaskType tt1=TaskType.id(1);
		System.out.println(tt);
		System.out.println(tt1);

	}

}
