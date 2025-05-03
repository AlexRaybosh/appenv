package appenv.susbsystems;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
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
import appenv.db.DB;
import appenv.db.StatementBlock;
import appenv.env.AppEnv;
import appenv.env.SubSystem;
import appenv.task.TaskHandler;
import appenv.task.TaskProcessingContext;
import appenv.task.TaskQueueBackend;
import appenv.task.TaskReader;
import appenv.task.TaskRecord;
import appenv.task.TaskState;
import appenv.task.TaskType;
import appenv.util.JsonUtils;
import appenv.util.UnorderedRow;
import appenv.util.Utils;



public class TaskQueueBackendSubsystem extends SubSystem implements TaskQueueBackend {

	public enum TaskProcessingState {
		START,SUCCESS,ERROR,POSTPONED
	};

	static class TaskProcessingEntry implements TaskProcessingContext {
		final TaskType taskType;
		final Future<TaskRecord> future;
		TaskProcessingState processingState=TaskProcessingState.START;
		byte[] result;
		String errorMsg;
		Throwable error;
		List<StatementBlock<Void>> statementBlocks=new ArrayList<>();
		List<TaskRecord> childrenTasks=new ArrayList<>();
		Long postponeMs;
		
		TaskProcessingEntry(TaskType taskType, Future<TaskRecord> f) {
			this.taskType=taskType;
			this.future=f;
		}
		public void success(byte[] result) {
			processingState=TaskProcessingState.SUCCESS;
			this.result=result;
		}
		public void postpone(Long newTimeMs) {
			processingState=TaskProcessingState.POSTPONED;
			postponeMs=newTimeMs;
		}	
		public void error(String msg, Throwable e) {
			processingState=TaskProcessingState.ERROR;
			errorMsg=msg;
			error=e;
		}
		public void addTaskOnCommit(TaskRecord next) {
			if (next!=null) childrenTasks.add(next);
		}
		public void addStatementBlockOnCommit(StatementBlock<Void> block) {
			if (block!=null) statementBlocks.add(block);
		}
		public final TaskType getTaskType() {
			return taskType;
		}		
		public TaskRecord getTaskRecord() throws InterruptedException {
			try {
				return future.get();
			} catch (ExecutionException e) {
				return Utils.rethrowRuntimeOrInterruptedException(e);
			}
		}
	}
	
	class TaskHandlerProcessor {
		final TaskType taskType;
		final TaskHandler taskHandler;
		final JsonElement taskConf;
		final int pickupSize;
		final int queueSize;
		final Service<Void> processService;
		final AtomicInteger inProcessCount=new AtomicInteger();
		volatile boolean canSignal=false;
		final boolean isDeleteOnSuccess;
		public TaskHandlerProcessor(TaskType taskType, TaskHandler taskHandler, JsonElement taskConf) {
			this.taskType = taskType;
			this.taskHandler = taskHandler;
			this.taskConf=taskConf;
			pickupSize=JsonUtils.getInteger(16, taskConf, "pickupSize");
			queueSize=JsonUtils.getInteger(pickupSize, taskConf, "queueSize");
			isDeleteOnSuccess=JsonUtils.getBoolean(false, taskConf, "deleteOnSuccess");
			processService=createTaskHandlerProcessingService(taskType, taskHandler, taskConf);
		}

		final CompletionCallback<Void> completionCallback=new CompletionCallback<Void>() {
			final void done() {
				int qcnt=inProcessCount.decrementAndGet();
				int qavail=queueSize-qcnt;
				if (qavail>0 && canSignal) {
					//System.out.println("signal "+taskType.getName()+" with qcnt: "+qcnt+" qavail: "+qavail);
					signalPickup();
				}
				
			}
			public void errored(Workload workload, Throwable e, Object[] args) {done();}
			public void completed(Workload workload, Void ret, Object[] args) {done();}
		};
		
		
		// ready to take a task for processing:
		public void submit(Future<TaskRecord> f) throws InterruptedException {
			//System.out.println(f);
			//int qcnt=
			inProcessCount.incrementAndGet();
			//int qavail=queueSize-qcnt;
			//System.out.println("submitted "+taskType.getName()+" with qcnt: "+qcnt+" qavail: "+qavail);
			processService.callWithCallbackNoLimit(completionCallback, f);
		}
		public int calculatePickupSize() {
			int qcnt=inProcessCount.intValue();
			int qavail=queueSize-qcnt;
			if (qavail>0) return Math.min(qavail, pickupSize);
			return 0;
		}
		public void hintFromPickup(int askedSize, int returnedSize) {
			canSignal = returnedSize==askedSize;
			
		}
		public boolean isDeleteOnSuccess() {
			return isDeleteOnSuccess;
		}
	}
	
	JsonObject conf;
	AsyncEngine asyncEngine;
	Service<Void> lookupCreateService;
	String dbName;
	String bulkExistentTaskLookupSql;
	Map<TaskType, TaskHandlerProcessor> taskHandlerProcessors=new LinkedHashMap<>();
	Future<Void> pickupLoopFuture;
	String pickupId=AppEnv.createUniqueKey();
	String pickupTaskSql="select ticket from task_queue q where env_type_id=? and task_type_id=? and task_state_id=? and process_at_ms<=? and not exists (select 1 from task_queue_process p where p.task_type_id=q.task_type_id and p.ticket=q.ticket) limit ?";
	String processPickupSql;
	String updatePickupSql;
	String updatePickedStatusSqlPrefix="update task_queue_process p join task_queue tq on (p.task_type_id=tq.task_type_id and p.ticket=tq.ticket) set tq.task_state_id=?, tq.system_process_id=?, tq.last_ms=? where p.pickup_id=?";
	String pickedTicketsSql= "select task_type_id, ticket from task_queue_process p where pickup_id=?";
	DB db;
	TaskReader taskReader;
	Service<Void> taskResultUpdateService;
	String deleteTaskFieldNumSql="delete q from common_tmp t join task_field_num q on (t.i1=q.task_type_id and t.t1=q.ticket)";
	String deleteTaskFieldTextSql="delete q from common_tmp t join task_field_text q on (t.i1=q.task_type_id and t.t1=q.ticket)";
	String deleteTaskErrorSql="delete q from common_tmp t join task_queue_error q on (t.i1=q.task_type_id and t.t1=q.ticket)";
	String deleteTaskQueueSql="delete q from common_tmp t join task_queue q on (t.i1=q.task_type_id and t.t1=q.ticket)";
	String deleteTaskQueueProcessSql="delete q from common_tmp t join task_queue_process q on (t.i1=q.task_type_id and t.t1=q.ticket)";
	
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
		db=AppEnv.db(dbName);
		pickupTaskSql=JsonUtils.getString(pickupTaskSql, conf, "pickupTaskSql");
		JsonObject allTasksConf = JsonUtils.getJsonObject(conf, "tasks");
		if (allTasksConf==null) return false;
		StringBuilder sbTp=new StringBuilder("insert into task_queue_process (task_type_id, ticket, pickup_id, system_process_id)\n");
		StringBuilder sbTypeList=new StringBuilder("(");
		boolean first=true;
		
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
				TaskHandlerProcessor taskHandlerProcessor=new TaskHandlerProcessor(taskType, taskHandler, taskConf);
				taskHandlerProcessors.put(taskType, taskHandlerProcessor);
				
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
		if (taskHandlerProcessors.isEmpty()) return false;
		processPickupSql = sbTp.toString();
		sbTypeList.append(")");
		//updatePickupSql="update tq set tq.task_state_id="+TaskState.PROCESS.getStateId()+ ", tq.system_process_id="+AppEnv.systemProcessId()+", tq.last_ms=? "
//					+ "from task_queue_process p join task_queue tq on (p.task_type_id=tq.task_type_id and p.ticket=tq.ticket) where p.pickup_id=?;
		updatePickedStatusSqlPrefix=JsonUtils.getString(updatePickedStatusSqlPrefix, conf, "updatePickedStatusSqlPrefix");
		updatePickupSql=updatePickedStatusSqlPrefix + (" and p.task_type_id in "+sbTypeList.toString() );
		pickedTicketsSql+=" and task_type_id in "+sbTypeList.toString();
		
		taskReader = new TaskReader(db, conf);
		taskResultUpdateService=createTaskResultUpdateService();

		
		deleteTaskFieldNumSql=JsonUtils.getString(deleteTaskFieldNumSql, conf, "deleteTaskFieldNumSql");
		deleteTaskFieldTextSql=JsonUtils.getString(deleteTaskFieldTextSql, conf, "deleteTaskFieldTextSql");
		deleteTaskErrorSql=JsonUtils.getString(deleteTaskErrorSql, conf, "deleteTaskErrorSql");
		deleteTaskQueueSql=JsonUtils.getString(deleteTaskQueueSql, conf, "deleteTaskQueueSql");
		deleteTaskQueueProcessSql=JsonUtils.getString(deleteTaskQueueProcessSql, conf, "deleteTaskQueueProcessSql");

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
		if (taskHandlerProcessors.isEmpty()) return false;
		List<Object> args=new ArrayList<>();
		Map<TaskType, UnorderedRow<Integer>> taskTypeToAskSizeAndReturnSize=new HashMap<>();
		String pickupId=AppEnv.createUniqueKey();
		for (TaskHandlerProcessor h : taskHandlerProcessors.values()) {
			
			args.add(pickupId);
			args.add(AppEnv.envTypeId());
			args.add(h.taskType.getId());
			args.add(TaskState.INIT.getStateId());
			args.add(time);			
			int limit=h.calculatePickupSize();
			args.add(limit);
			taskTypeToAskSizeAndReturnSize.put(h.taskType, new UnorderedRow<>(limit,0));
		}
		List<Object[]> typeIdTicketList=db.commit(new StatementBlock<List<Object[]>>() {
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
			UnorderedRow<Integer> askSizeAndReturnSize = taskTypeToAskSizeAndReturnSize.get(taskType);
			askSizeAndReturnSize.get()[1]=askSizeAndReturnSize.get()[1]+1;
			String ticket=Utils.toString(row[1]);
			TaskHandlerProcessor hc = taskHandlerProcessors.get(taskType);
			Future<TaskRecord> f = taskReader.lookup(taskType, ticket);
			hc.submit(f);
		}
		for (TaskHandlerProcessor h : taskHandlerProcessors.values()) {
			UnorderedRow<Integer> askSizeAndReturnSize = taskTypeToAskSizeAndReturnSize.get(h.taskType);			
			h.hintFromPickup(askSizeAndReturnSize.get()[0], askSizeAndReturnSize.get()[1]);			
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


	private Service<Void> createTaskHandlerProcessingService(TaskType taskType, TaskHandler taskHandler, JsonElement taskConf) {
		return asyncEngine.register("process_"+taskType.getName(), new ServiceBackend<Void>() {	
			public void process(List<Request<Void>> bulk) throws Exception {
				List<TaskProcessingEntry> pl=new ArrayList<>(bulk.size());
				for (Request<Void> r : bulk) {
					Future<TaskRecord> f=r.arg(0);
					TaskProcessingEntry c=new TaskProcessingEntry(taskType, f);
					pl.add(c);
				}
				processInTaskHandler(taskHandler, pl);
			}

			public int getMaxWorkers() {return JsonUtils.getInteger(1, taskConf, "concurrency");}
			public int getMaxQueuedRequests() {return 1;} // dont use it, we use inProcessCount
			public int getMaxBulkSize() {
				return JsonUtils.getInteger(1, taskConf, "processBulkSize");
			}
		});
	}
	
	private void processInTaskHandler(TaskHandler taskHandler, List<TaskProcessingEntry> pl) throws InterruptedException {
		Exception error=null;
		try {
			taskHandler.process(pl);
		} catch (Throwable e) {
			error=Utils.proceedUnlessInterrupted(e);
		} finally {
			for (TaskProcessingEntry c : pl) {
				// we don't need to hold payload anymore:
				try {c.future.get().setPayload(null);} catch (Exception xxx) {Utils.proceedUnlessInterrupted(xxx);}
				if (c.processingState==TaskProcessingState.START) c.error(error);
				// Ready to submit save the result to the db
				taskResultUpdateService.call(asyncEngine, c);
			}
		}
	}
	
	private Service<Void> createTaskResultUpdateService() {
		return asyncEngine.register("taskResultUpdateService", new ServiceBackend<Void>() {	
			public void process(List<Request<Void>> bulk) throws Exception {
				List<TaskProcessingEntry> entries=new LinkedList<>();
				for (Request<Void> r : bulk) {
					TaskProcessingEntry e=r.arg(0);
					entries.add(e);
				}
				if (entries.isEmpty()) return;
				try {
					updateTaskResult(entries);
				} catch (SQLException ex) {
					Map<TaskProcessingEntry, Exception> fatals=new HashMap<>();
					if (entries.size()>1) for (TaskProcessingEntry e : entries) {
						try {
							updateTaskResult(Collections.singletonList(e));
						} catch (SQLException ex1) {
							fatals.put(e, ex1);
							AppEnv.logerr("Failed on updating "+e.getTaskRecord().getTaskType().getName(), ex1);
						}						
					} else {
						TaskProcessingEntry e = entries.get(0);
						fatals.put(e, ex);
						AppEnv.logerr("Failed on updating "+e.getTaskRecord().getTaskType().getName(), ex);						
					}
					if (!fatals.isEmpty()) tryUpdateFatals(fatals);
				}
			}

			public int getMaxWorkers() {return JsonUtils.getInteger(1, conf, "resultUpdateConcurrency");}
			public int getMaxQueuedRequests() {return JsonUtils.getInteger(128, conf, "resultUpdateQueueSize");}
			public int getMaxBulkSize() {return JsonUtils.getInteger(128, conf, "resultUpdateBulkSize");} 
		});
	}

	protected void tryUpdateFatals(Map<TaskProcessingEntry, Exception> fatals) {
		// TODO Auto-generated method stub
		
	}
	
	private void updateTaskResult(List<TaskProcessingEntry> entries) throws SQLException, InterruptedException {
		final List<Object[]> deleteOnSuccessRows=new ArrayList<>();
		final List<Object[]> postponedRows=new ArrayList<>();
		final List<Object[]> successUpdateRows=new ArrayList<>();
		final List<TaskRecord> childrenTasks=new ArrayList<>();
		final List<StatementBlock<Void>> statementBlocks=new ArrayList<>();
		for (TaskProcessingEntry te : entries) {
			TaskRecord taskRecord = te.getTaskRecord();
			TaskType taskType = taskRecord.getTaskType();
			TaskHandlerProcessor taskHandlerProcessor = taskHandlerProcessors.get(taskType);
			if (te.processingState==TaskProcessingState.SUCCESS ) {
				if (taskHandlerProcessor.isDeleteOnSuccess()) deleteOnSuccessRows.add(new Object[] {taskType.getId(), taskRecord.getTicket()});
				else successUpdateRows.add(new Object[] {taskType.getId(), taskRecord.getTicket(),TaskState.SUCCESS.getStateId(), te.result ,AppEnv.getTime()});
				
				childrenTasks.addAll(te.childrenTasks);
				statementBlocks.addAll(te.statementBlocks);					
				
				continue;
			}
			if (te.processingState==TaskProcessingState.POSTPONED) {
				Long timeMs=te.postponeMs==null?AppEnv.getTime():te.postponeMs;
				postponedRows.add(new Object[] {taskType.getId(), taskRecord.getTicket(), TaskState.INIT.getStateId(), timeMs, AppEnv.getTime()});
				continue;
			} else {
				// we got an error
			}
		}
		db.commit(new StatementBlock<Void>() {
			@Override
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {

				deleteTasks(cw, deleteOnSuccessRows);
					
				
				return null;
			}


		});
	}
	private void deleteTasks(ConnectionWrap cw, List<Object[]> rows) throws SQLException, InterruptedException {
		if (rows.isEmpty()) return;
		else if (rows.size()>2) {
			if (cw.needsTempTableCleanup()) cw.update("delete from common_tmp", true);
			cw.batchInsert("insert into common_tmp (i1, t1) values (?, ?)",rows);
			for (String sql : new String[] {deleteTaskFieldNumSql,deleteTaskFieldTextSql,deleteTaskErrorSql, deleteTaskQueueSql, deleteTaskQueueProcessSql}) {
				cw.update(sql,true);						
			}
			if (cw.needsTempTableCleanup()) cw.update("delete from common_tmp", true);
		} else {
			for (Object[] row : rows) {
				cw.update("delete from task_field_num where task_type_id=? and ticket=?", true, row);
				cw.update("delete from task_field_text where task_type_id=? and ticket=?", true, row);
				cw.update("delete from task_queue_error where task_type_id=? and ticket=?", true, row);
				cw.update("delete from task_queue where task_type_id=? and ticket=?", true, row);
				cw.update("delete from task_queue_process where task_type_id=? and ticket=?", true, row);						
			}
		}
	}

}
