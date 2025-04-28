package appenv.susbsystems;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import appenv.async.AsyncEngine;
import appenv.async.CompletionCallback;
import appenv.async.Request;
import appenv.async.Service;
import appenv.async.ServiceBackend;
import appenv.async.Workload;
import appenv.db.ConnectionWrap;
import appenv.db.StatementBlock;
import appenv.env.AppEnv;
import appenv.env.SubSystem;
import appenv.task.TaskHandler;
import appenv.task.TaskQueueBackend;
import appenv.task.TaskState;
import appenv.task.TaskType;
import appenv.util.JsonUtils;
import appenv.util.Utils;



public class TaskQueueBackendSubsystem extends SubSystem implements TaskQueueBackend {
	
	class TaskHandlerContext {
		final TaskType taskType;
		final TaskHandler taskHandler;
		final JsonElement taskConf;
		final int pickupSize;
		final Service<Void> processService;
		final AtomicInteger inProcessCount=new AtomicInteger();
		public TaskHandlerContext(TaskType taskType, TaskHandler taskHandler, JsonElement taskConf) {
			this.taskType = taskType;
			this.taskHandler = taskHandler;
			this.taskConf=taskConf;
			pickupSize=JsonUtils.getInteger(16, taskConf, "pickupSize");
			processService=asyncEngine.register("process_"+taskType.getName(), new ServiceBackend<Void>() {	
				public void process(List<Request<Void>> bulk) throws Exception {
					for (Request<Void> r : bulk) {
						String ticket=r.arg(0);
						System.out.println(ticket);
					}
				}
				public int getMaxWorkers() {return JsonUtils.getInteger(1, taskConf, "concurrency");}
				public int getMaxQueuedRequests() {return 1;}
				public int getMaxBulkSize() {
					return JsonUtils.getInteger(1, taskConf, "processBulkSize");
				}
			});
		}
		final CompletionCallback<Void> completionCallback=new CompletionCallback<Void>() {
			public void errored(Workload workload, Throwable e, Object[] args) {
				inProcessCount.decrementAndGet();
				System.out.println("errored");
			}
			public void completed(Workload workload, Void ret, Object[] args) {
				inProcessCount.decrementAndGet();
				System.out.println("completed");
			}
		};
		public void submit(String ticket) throws InterruptedException {
			System.out.println(ticket);
			inProcessCount.incrementAndGet();
			processService.callWithCallbackNoLimit(completionCallback, ticket);
		}
	}
	
	JsonObject conf;
	AsyncEngine asyncEngine;
	Service<Void> lookupCreateService;
	String dbName;
	String bulkExistentTaskLookupSql;
	Map<TaskType, TaskHandlerContext> handlers=new LinkedHashMap<>();
	Future<Void> pickupLoopFuture;
	String pickupId=AppEnv.createUniqueKey();
	String pickupTaskSql="select ticket from task_queue q where env_type_id=? and task_type_id=? and task_state_id=? and process_at_ms<=? and not exists (select 1 from task_queue_process p where p.task_type_id=q.task_type_id and p.ticket=q.ticket) limit ?";
	String processPickupSql;
	String updatePickupSql;
	String updatePickedStatusSqlPrefix="update task_queue_process p join task_queue tq on (p.task_type_id=tq.task_type_id and p.ticket=tq.ticket) set tq.task_state_id=?, tq.system_process_id=?, tq.last_ms=? where p.pickup_id=?";
	String pickedTicketsSql= "select task_type_id, ticket from task_queue_process p where pickup_id=?";
	
	@Override
	public boolean init(boolean initial, JsonObject c) throws Exception {
		this.conf=c;		
		System.out.println(JsonUtils.prettyPrint(conf));
		if (AppEnv.envTypeId()==null) throw new RuntimeException("Environment "+AppEnv.envTypeName()+"' is not registered in the DB");
		if (AppEnv.systemProcessId()==null) throw new RuntimeException("TaskQueueBackendSubsystem requires processMaintenance subsystem enabled");
		asyncEngine=AsyncEngine.create();
		/*
		lookupCreateService=asyncEngine.register("lookupCreateByName", new ServiceBackend<Void>() {	
			public void process(List<Request<Void>> bulk) throws Exception {insert(bulk);}
			public int getMaxWorkers() {return JsonUtils.getInteger(1, conf, "insertConcurrency");}
			public int getMaxQueuedRequests() {return JsonUtils.getInteger(1, conf, "insertQueueSize");}
			public int getMaxBulkSize() {
				return JsonUtils.getInteger(1, conf, "insertBulkSize");
			}
		});*/
		dbName=JsonUtils.getString("core", conf, "database");
		pickupTaskSql=JsonUtils.getString(pickupTaskSql, conf, "pickupTaskSql");
		JsonObject allTasksConf = JsonUtils.getJsonObject(conf, "tasks");
		StringBuilder sbTp=new StringBuilder("insert into task_queue_process (task_type_id, ticket, pickup_id, system_process_id)\n");
		StringBuilder sbTypeList=new StringBuilder("(");
		boolean first=true;
		if (allTasksConf!=null) {
			for (Entry<String, JsonElement> e : allTasksConf.entrySet()) {
				String typeName=e.getKey();
				JsonElement taskConf = e.getValue();
				try {
					TaskType taskType=TaskType.name(typeName);
					if (taskConf.isJsonNull()) continue;
					if (!JsonUtils.getBoolean(true, taskConf, "enabled")) continue;
					if (!taskConf.isJsonObject()) throw new RuntimeException(typeName+" must be a json object or null");
					String className=JsonUtils.getString(taskConf, "class");
					if (className==null) continue;
					TaskHandler taskHandler = (TaskHandler)Class.forName(className).getConstructor().newInstance();
					taskHandler.init(taskType, taskConf.getAsJsonObject());
					TaskHandlerContext taskHandlerContext=new TaskHandlerContext(taskType, taskHandler, taskConf);
					handlers.put(taskType, taskHandlerContext);
					
					if (!first) sbTp.append("\nunion all\n");
					sbTp.append("select "+taskType.getId()+" as task_type_id, ticket, ? as pickup_id, "+AppEnv.systemProcessId()+" as system_process_id from (\n");
					sbTp.append(pickupTaskSql);
					sbTp.append("\n) as t_"+taskType.getId());
					
					if (!first) sbTypeList.append(",");
					sbTypeList.append(taskType.getId());
					first=false;
				} catch (Throwable t) {
					Exception ex = Utils.proceedUnlessInterrupted(t);
					throw new RuntimeException("Failed to initialize task handler with type: '"+typeName+"', and conf:"+taskConf, ex);
				}				
			}
			processPickupSql = sbTp.toString();
			sbTypeList.append(")");
			//updatePickupSql="update tq set tq.task_state_id="+TaskState.PROCESS.getStateId()+ ", tq.system_process_id="+AppEnv.systemProcessId()+", tq.last_ms=? "
//					+ "from task_queue_process p join task_queue tq on (p.task_type_id=tq.task_type_id and p.ticket=tq.ticket) where p.pickup_id=?;
			updatePickedStatusSqlPrefix=JsonUtils.getString(updatePickedStatusSqlPrefix, conf, "updatePickedStatusSqlPrefix");
			boolean addTypeIds=JsonUtils.getBoolean(true, conf, "addPickedStatusSqlAddTaskTypeCondition");
			updatePickupSql=updatePickedStatusSqlPrefix + (addTypeIds ? ("  and p.task_type_id in "+sbTypeList.toString() ) : "");
			pickedTicketsSql+=" and task_type_id in "+sbTypeList.toString();
			
			System.out.println(processPickupSql);
			
		}
		if (handlers.isEmpty()) return false;
		pickupLoopFuture = AsyncEngine.getEngineExecutorService().submit(new Callable<Void>() {
			public Void call() throws Exception {
				for (;;) {
					synchronized (lock) {
						if (!pickupThreadReady) {
							pickupThreadReady=true;
							lock.notify();
						}
						if (!pickupSignalRaised) lock.wait();
						pickupSignalRaised=false;
					}
					try {
						while (pickup());
					} catch (Exception e) {
						e=Utils.proceedUnlessInterrupted(e);
						AppEnv.logerr("Task pickup failed: ",e);
					}
				}
			}
		});
		synchronized (lock) {
			while (!pickupThreadReady) {
				lock.wait();
			}
		}
		return true;
	}
	boolean pickupThreadReady=false;
	boolean pickupSignalRaised=false;
	Object lock=new Object();
	private void signalPickup() {
		synchronized (lock) {
			lock.notify();
			pickupSignalRaised=true;
		}
	}	


	private boolean pickup() throws InterruptedException, SQLException {
		long time=AppEnv.getTime();
		System.out.println("pickup: "+Utils.formatLocalDateTime(new Date(time)));
		if (handlers.isEmpty()) return false;
		List<Object> args=new ArrayList<>();
		String pickupId=AppEnv.createUniqueKey();
		for (TaskHandlerContext h : handlers.values()) {
			args.add(pickupId);
			args.add(AppEnv.envTypeId());
			args.add(h.taskType.getId());
			args.add(TaskState.INIT.getStateId());
			args.add(time);
			int limit=h.pickupSize;
			args.add(limit);
		}
		List<Object[]> typeIdTicketList=AppEnv.db(dbName).commit(new StatementBlock<List<Object[]>>() {
			public List<Object[]> execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				int cnt=cw.update(processPickupSql, true,args.toArray(new Object[0]));
				if (cnt==0) return Collections.emptyList();
				cw.update(updatePickupSql, true, TaskState.PROCESS.getStateId(), AppEnv.systemProcessId(), AppEnv.getTime(), pickupId);
				return cw.select(pickedTicketsSql, true, pickupId);
			}
			@Override
			public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
				//ex.printStackTrace();
				return super.onError(cw, willAttemptToRetry, ex, start, now);
			}
		});
		for (Object[] row : typeIdTicketList) {
			TaskType taskType=TaskType.id(Utils.toInteger(row[0]));
			String ticket=Utils.toString(row[1]);
			TaskHandlerContext hc = handlers.get(taskType);
			hc.submit(ticket);
		}
		
		return !typeIdTicketList.isEmpty();
	}
	@Override
	public void destroy() {
		if (pickupLoopFuture!=null) pickupLoopFuture.cancel(true);
	}

	public final static String PICKUP="pickup";
	@Override
	public boolean tick(long startAfterMs, long intervalMs, String tickName, Long lastRun) throws Exception {
		if (PICKUP.equals(tickName)) {
			signalPickup();
		}
		return true;
	}


}
