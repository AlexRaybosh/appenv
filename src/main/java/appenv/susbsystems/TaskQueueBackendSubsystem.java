package appenv.susbsystems;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import appenv.db.DB.Dialect;
import appenv.db.StatementBlock;
import appenv.env.AppEnv;
import appenv.env.SubSystem;
import appenv.task.TaskHandler;
import appenv.task.TaskInserter;
import appenv.task.TaskCompletionListener;
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
		Long lastMs;
		
		TaskProcessingEntry(TaskType taskType, Future<TaskRecord> f) {
			this.taskType=taskType;
			this.future=f;
		}
		public void success(byte[] result) {
			processingState=TaskProcessingState.SUCCESS;
			this.result=result;
			lastMs=AppEnv.getTime();
		}
		public void postpone(Long newTimeMs) {
			processingState=TaskProcessingState.POSTPONED;
			postponeMs=newTimeMs;
			lastMs=AppEnv.getTime();
		}	
		public void error(String msg, Throwable e) {
			processingState=TaskProcessingState.ERROR;
			errorMsg=msg;
			error=e;
			lastMs=AppEnv.getTime();
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
		int maxErrorCount=10;
		public TaskHandlerProcessor(TaskType taskType, TaskHandler taskHandler, JsonElement taskConf) {
			this.taskType = taskType;
			this.taskHandler = taskHandler;
			this.taskConf=taskConf;
			pickupSize=JsonUtils.getInteger(16, taskConf, "pickupSize");
			queueSize=JsonUtils.getInteger(pickupSize, taskConf, "queueSize");
			maxErrorCount=JsonUtils.getInteger(pickupSize, taskConf, "maxErrorCount");
			isDeleteOnSuccess=JsonUtils.getBoolean(false, taskConf, "deleteOnSuccess");
			processService=createTaskHandlerProcessingService(taskType, taskHandler, taskConf);
		}

		final CompletionCallback<Void> completionCallback=new CompletionCallback<Void>() {
			final void done() {
				int qcnt=inProcessCount.decrementAndGet();
				int qavail=queueSize-qcnt;
				if (qavail>0) {
					//System.out.println("signal "+taskType.getName()+" with qcnt: "+qcnt+" qavail: "+qavail);
					signalPickup();
				}
				
			}
			public void errored(Workload workload, Throwable e, Object[] args) {done();}
			public void completed(Workload workload, Void ret, Object[] args) {done();}
		};
				
		// ready to take a task for processing:
		public void submit(Future<TaskRecord> f) throws InterruptedException {
			inProcessCount.incrementAndGet();
			processService.callWithCallbackNoLimit(completionCallback, f);
		}
		public int calculatePickupSize() {
			int qcnt=inProcessCount.intValue();
			int qavail=queueSize-qcnt;
			if (qavail>0) return Math.min(qavail, pickupSize);
			return 0;
		}
		public boolean isDeleteOnSuccess() {
			return isDeleteOnSuccess;
		}
	}
	
	JsonObject conf;
	AsyncEngine asyncEngine;
	Service<Void> lookupCreateService;
	String dbName;

	Map<TaskType, TaskHandlerProcessor> taskHandlerProcessors=new LinkedHashMap<>();
	List<Future<Void>> pickupLoopFutures=new ArrayList<>();
	String pickupId=AppEnv.createUniqueKey();
	String pickupTaskSql="select ticket from task_queue q where env_type_id=? and task_type_id=? and task_state_id=? and process_at_ms<=? and not exists (select 1 from task_queue_process p where p.task_type_id=q.task_type_id and p.ticket=q.ticket) limit ?";
	String processPickupSql;
	String updatePickupSql;
	String updatePickedStatusSqlPrefix="update task_queue_process p join task_queue tq on (p.task_type_id=tq.task_type_id and p.ticket=tq.ticket) set tq.task_state_id=?, tq.system_process_id=?, tq.last_ms=? where p.pickup_id=?";
	String pickedTicketsSql= "select task_type_id, ticket from task_queue_process p where pickup_id=?";
	DB db;
	TaskReader taskReader;
	TaskInserter taskInserter;
	Service<Void> taskResultUpdateService;
	String deleteTaskFieldNumSql="delete q from common_tmp t join task_field_num q on (t.i1=q.task_type_id and t.t1=q.ticket)";
	String deleteTaskFieldTextSql="delete q from common_tmp t join task_field_text q on (t.i1=q.task_type_id and t.t1=q.ticket)";
	String deleteTaskErrorSql="delete q from common_tmp t join task_queue_error q on (t.i1=q.task_type_id and t.t1=q.ticket)";
	String deleteTaskQueueSql="delete q from common_tmp t join task_queue q on (t.i1=q.task_type_id and t.t1=q.ticket)";
	String deleteTaskQueueProcessSql="delete q from common_tmp t join task_queue_process q on (t.i1=q.task_type_id and t.t1=q.ticket)";

	//i1  - task_type_id, t1 - ticket, i2 task_state_id, b1 - result, i3 - last_ms	
	String updateTaskQueueStatusSql="update common_tmp t join task_queue q on (t.i1=q.task_type_id and t.t1=q.ticket) set q.task_state_id=t.i2, q.result=t.b1, q.last_ms=t.i3";
	//                                task_type_id=i1    ticket=t1      task_state_id=i2          error_count=i3        last_ms=i4       error_id=i5   error_message=t2 error=t3
	String updateTaskQueueErrorSql="update common_tmp t join task_queue q on (t.i1=q.task_type_id and t.t1=q.ticket) set q.task_state_id=t.i2, q.error_count=t.i3, q.last_ms=t.i4";
	
	//i1  - task_type_id, t1 - ticket, i2 task_state_id, i3 - process_at_ms, i4 - last_ms
	String updateTaskQueuePostponeSql="update common_tmp t join task_queue q on (t.i1=q.task_type_id and t.t1=q.ticket) set q.task_state_id=t.i2, q.process_at_ms=t.i3, q.last_ms=t.i4";
	int transactionRetryCount=1;
	
	String movePickedToProcessSql="insert into task_queue_process (task_type_id, ticket, pickup_id, system_process_id) select i1, t1, ? as pickup_id, ? as system_process_id from common_tmp t where not exists (select 1 from task_queue_process p where p.task_type_id=t.i1 and p.ticket=t.t1)";
	/*
		"pickupSingleMove" :true,
		"#pickupSingleMove comment" : "don't split picking from task_queue into common_tmp, and moving into task_queue_process from common_tmp, use a single sql, wont work with postgres", 
		"pickupTaskSql" : "select ticket from task_queue q FORCE INDEX (task_pickup_idx) where env_type_id=? and task_type_id=? and task_state_id=? and process_at_ms<=? and not exists (select 1 from task_queue_process p where p.task_type_id=q.task_type_id and p.ticket=q.ticket) limit ?",
		"movePickedToProcessSql" : "insert into task_queue_process (task_type_id, ticket, pickup_id, system_process_id) select i1, t1, ? as pickup_id, ? as system_process_id from common_tmp t where not exists (select 1 from task_queue_process p where p.task_type_id=t.i1 and p.ticket=cast(t.t1 as char))",
		
	 */
	boolean pickupSingleMove=false;
	String pickupSingleMoveSql;
	
	@Override
	public boolean init(boolean initial, JsonObject c) throws Exception {
		this.conf=c;		
		System.out.println(JsonUtils.prettyPrint(conf));
		if (AppEnv.envTypeId()==null) throw new RuntimeException("Environment "+AppEnv.envTypeName()+"' is not registered in the DB");
		if (AppEnv.systemProcessId()==null) throw new RuntimeException("TaskQueueBackendSubsystem requires processMaintenance subsystem enabled");
		asyncEngine=AsyncEngine.create();

		dbName=JsonUtils.getString("core", conf, "database");
		db=AppEnv.db(dbName);
		
		pickupTaskSql=db.getConfDialectStringProperty(pickupTaskSql, conf, "pickupTaskSql");
		
		JsonObject allTasksConf = JsonUtils.getJsonObject(conf, "tasks");
		if (allTasksConf==null) return false;
		
		pickupSingleMove=db.getConfDialectBooleanProperty(db.getDialect()!=Dialect.POSTGRES, conf, "pickupSingleMove");
		
		StringBuilder sbTp=new StringBuilder("insert into common_tmp (i1, t1) "
				+ "\n"
				+ "select pickup.task_type_id, pickup.ticket from (\n");
		StringBuilder sbTypeList=new StringBuilder("(");
		boolean first=true;

		StringBuilder singleMove=new StringBuilder("insert into task_queue_process (task_type_id, ticket, pickup_id, system_process_id) "
				+ "select pickup.task_type_id, pickup.ticket, ? as pickup_id, ? as system_process_id from (\n");
		
		
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
				
				if (!first) {
					sbTp.append("\nunion all\n");
					singleMove.append("\nunion all\n");
				}
				sbTp.append("select "+taskType.getId()+" as task_type_id, ticket from (\n");
				sbTp.append(pickupTaskSql);
				sbTp.append("\n) as t_"+taskType.getId());
				
				singleMove.append("select "+taskType.getId()+" as task_type_id, ticket from (\n");
				singleMove.append(pickupTaskSql);
				singleMove.append("\n) as t_"+taskType.getId());
				
				if (!first) sbTypeList.append(",");
				sbTypeList.append(taskType.getId());				
				first=false;
			} catch (Throwable t) {
				Exception ex = Utils.proceedUnlessInterrupted(t);
				throw new RuntimeException("Failed to initialize task handler with type: '"+typeName+"', and conf:"+taskConf, ex);
			}				
		}
		if (taskHandlerProcessors.isEmpty()) return false;
		sbTp.append("\n) as pickup");
		processPickupSql = sbTp.toString();
		singleMove.append("\n) as pickup");
		pickupSingleMoveSql = singleMove.toString();

		
		sbTypeList.append(")");
		
		updatePickedStatusSqlPrefix=db.getConfDialectStringProperty(updatePickedStatusSqlPrefix, conf, "updatePickedStatusSqlPrefix");
		updatePickupSql=updatePickedStatusSqlPrefix + (" and p.task_type_id in "+sbTypeList.toString() );
		pickedTicketsSql+=" and task_type_id in "+sbTypeList.toString();
		
		taskReader = new TaskReader(db, conf);
		taskInserter = new TaskInserter(db, conf);
		taskResultUpdateService=createTaskResultUpdateService();

		
		deleteTaskFieldNumSql=db.getConfDialectStringProperty(deleteTaskFieldNumSql, conf, "deleteTaskFieldNumSql");
		deleteTaskFieldTextSql=db.getConfDialectStringProperty(deleteTaskFieldTextSql, conf, "deleteTaskFieldTextSql");
		deleteTaskErrorSql=db.getConfDialectStringProperty(deleteTaskErrorSql, conf, "deleteTaskErrorSql");
		deleteTaskQueueSql=db.getConfDialectStringProperty(deleteTaskQueueSql, conf, "deleteTaskQueueSql");
		deleteTaskQueueProcessSql=db.getConfDialectStringProperty(deleteTaskQueueProcessSql, conf, "deleteTaskQueueProcessSql");
		updateTaskQueueStatusSql=db.getConfDialectStringProperty(updateTaskQueueStatusSql, conf, "updateTaskQueueStatusSql");
		updateTaskQueuePostponeSql=db.getConfDialectStringProperty(updateTaskQueuePostponeSql, conf, "updateTaskQueuePostponeSql");
		updateTaskQueueErrorSql=db.getConfDialectStringProperty(updateTaskQueueErrorSql, conf, "updateTaskQueueErrorSql");
		movePickedToProcessSql=db.getConfDialectStringProperty(movePickedToProcessSql, conf, "movePickedToProcessSql");
		transactionRetryCount=db.getConfDialectIntProperty(1, conf, "transactionRetryCount");
		
		int pickupConcurrency=db.getConfDialectIntProperty(1, conf, "pickupConcurrency");
		
		for (int i=0;i<pickupConcurrency;++i) {
			Future<Void> pickupLoopFuture = AsyncEngine.getEngineExecutorService().submit(new Callable<Void>() {
				Set<String> pickupIds=new LinkedHashSet<>();
				public Void call() throws Exception {
					for (;;) {
						synchronized (lock) {
							/*if (!pickupThreadReady) {
								pickupThreadReady=true;
								lock.notifyAll();
							}
							/*if (pickupSignalRaised==0)*/ 
							lock.wait();
							//if (pickupSignalRaised>0) --pickupSignalRaised;
						}
						try {
							while (pickup(pickupIds));
						} catch (Exception e) {
							e=Utils.proceedUnlessInterrupted(e);
							AppEnv.logerr("Task pickup failed: ",e);
						}
					}
				}
			});
			pickupLoopFutures.add(pickupLoopFuture);
		}
		/*synchronized (lock) {
			while (!pickupThreadReady) {
				lock.wait();
			}
		}
		*/
		return true;
	}
	Object lock=new Object();
	private void signalPickup() {
		synchronized (lock) {
			lock.notify();
		}
	}
	private void signalAllPickup() {
		synchronized (lock) {
			lock.notifyAll();
		}
	}	


	private boolean pickup(Set<String> pickupIds) throws InterruptedException, SQLException {
		//System.out.println("pickup: "+Utils.formatLocalDateTime(new Date()) + "\t"+Thread.currentThread());
		if (taskHandlerProcessors.isEmpty()) return false;
		boolean mightHaveMore=false;
		boolean isEmptyPickup=true;
		if (pickupIds.isEmpty()) {
			String pickupId=AppEnv.createUniqueKey();
			pickupIds.add(pickupId);
			
			List<Object> args=new ArrayList<>();
			long time=AppEnv.getTime();
			if (pickupSingleMove) {
				args.add(pickupId);
				args.add(AppEnv.systemProcessId());
			}
			for (TaskHandlerProcessor h : taskHandlerProcessors.values()) {			
				args.add(AppEnv.envTypeId());
				args.add(h.taskType.getId());
				args.add(TaskState.INIT.getStateId());
				args.add(time);			
				int limit=h.calculatePickupSize();
				args.add(limit);
			}
			UnorderedRow<Integer> stats=db.commit(new StatementBlock<UnorderedRow<Integer>>() {
				int errorCount=0;
				public UnorderedRow<Integer> execute(ConnectionWrap cw) throws SQLException, InterruptedException {
					if (pickupSingleMove) {
						int cnt=cw.update(pickupSingleMoveSql, true,args.toArray(new Object[0]));
						if (cnt>0) cw.update(updatePickupSql, true, TaskState.PROCESS.getStateId(), AppEnv.systemProcessId(), AppEnv.getTime(), pickupId);
						return new UnorderedRow<Integer>(cnt, cnt);
					} else {
						if (cw.needsTempTableCleanup()) cw.update("delete from common_tmp", true);
						int tmpPickupCounts=cw.update(processPickupSql, true,args.toArray(new Object[0]));
						int movedToProcessCnt=tmpPickupCounts==0?0:cw.update(movePickedToProcessSql, true, pickupId, AppEnv.systemProcessId());
						if (movedToProcessCnt>0) cw.update(updatePickupSql, true, TaskState.PROCESS.getStateId(), AppEnv.systemProcessId(), AppEnv.getTime(), pickupId);
						if (tmpPickupCounts>0 && cw.needsTempTableCleanup()) cw.update("delete from common_tmp", true);
						return new UnorderedRow<Integer>(tmpPickupCounts, movedToProcessCnt);
					}
				}
				@Override
				public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {

					if (ex!=null && ex.getMessage()!=null && ex.getMessage().toLowerCase().contains("deadlock")) {
						if (++errorCount < transactionRetryCount) {
							//System.err.println("ERROR UPDATING STATUS: "+errorCount+" : "+ex.getMessage());
							return false;
						}
					}
					return super.onError(cw, willAttemptToRetry, ex, start, now);
				}
			});
			int tmpPickupCounts=stats.get()[0];
			int movedToProcessCnt=stats.get()[1]; 
			mightHaveMore=tmpPickupCounts>0;
			isEmptyPickup = movedToProcessCnt==0;
			//System.out.println("pickup tmpPickupCounts: "+tmpPickupCounts+ ", movedToProcessCnt : "+movedToProcessCnt+" mightHaveMore: "+mightHaveMore+"\t"+Thread.currentThread());
			if (tmpPickupCounts>0 && tmpPickupCounts==movedToProcessCnt) {
				//System.out.println("WAKE UP ALL");
				signalAllPickup();
			}
			if (tmpPickupCounts>0 && movedToProcessCnt==0) {
				//System.out.println("OVERSATURATION!!!!");
			}
		}
		while (!pickupIds.isEmpty()) {
			String pickupId=pickupIds.iterator().next();
			List<Object[]> typeIdTicketList=isEmptyPickup?Collections.emptyList(): db.select(pickedTicketsSql, true, pickupId);
			if (!typeIdTicketList.isEmpty()) mightHaveMore=true;
			for (Object[] row : typeIdTicketList) {
				TaskType taskType=TaskType.id(Utils.toInteger(row[0]));
				String ticket=Utils.toString(row[1]);
				TaskHandlerProcessor hc = taskHandlerProcessors.get(taskType);
				Future<TaskRecord> f = taskReader.lookup(taskType, ticket);
				hc.submit(f);
			}
			pickupIds.remove(pickupId);
		}
		return !isEmptyPickup; // we will punish, if its empty, even if we have more
		//return mightHaveMore;
	}
	@Override
	public void destroy() {
		for (Future<Void> pickupLoopFuture : pickupLoopFutures) {
			pickupLoopFuture.cancel(true);	
		} 
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
				} catch (Exception ex) {
					Map<TaskProcessingEntry, Exception> fatals=new HashMap<>();
					for (TaskProcessingEntry e : entries) {
						try {
							updateTaskResult(Collections.singletonList(e));
						} catch (Exception ex1) {
							ex1=Utils.proceedUnlessInterrupted(ex1);
							fatals.put(e, ex1);
							e.getTaskRecord().setTaskState(TaskState.FATAL);
							AppEnv.logerr("Failed on updating "+e.getTaskRecord().getTaskType().getName(), ex1);
						}						
					}
					if (!fatals.isEmpty()) tryUpdateFatals(fatals);
				}
			}

			public int getMaxWorkers() {return JsonUtils.getInteger(1, conf, "resultUpdateConcurrency");}
			public int getMaxQueuedRequests() {return JsonUtils.getInteger(128, conf, "resultUpdateQueueSize");}
			public int getMaxBulkSize() {return JsonUtils.getInteger(128, conf, "resultUpdateBulkSize");} 
		});
	}

	protected void tryUpdateFatals(Map<TaskProcessingEntry, Exception> fatals) throws SQLException, InterruptedException {
		for (Entry<TaskProcessingEntry, Exception> e : fatals.entrySet()) {
			TaskProcessingEntry pe=e.getKey();
			Exception ex = e.getValue();
			Long errorId=AppEnv.newId("task_queue_error_id");
			db.commit(new StatementBlock<Void>() {
				@Override
				public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
					cw.update("delete from task_queue_process where task_type_id=? and ticket=?", true, pe.getTaskRecord().getTaskTypeId(), pe.getTaskRecord().getTicket());
					cw.update("update task_queue set task_state_id=?, last_ms=?  where task_type_id=? and ticket=?", true, TaskState.FATAL.getStateId(), AppEnv.getTime(), pe.getTaskRecord().getTaskTypeId(), pe.getTaskRecord().getTicket());
					cw.update("insert into task_queue_error (id, task_type_id, ticket, last_ms, message, error, system_process_id) values (?, ?, ?, ?, ?, ?, ?)", true,
							errorId,
							pe.getTaskRecord().getTaskTypeId(), 
							pe.getTaskRecord().getTicket(),
							AppEnv.getTime(),
							"Fatal failure to commit the result",
							Utils.getStackTrace(ex),
							AppEnv.systemProcessId());
					return null;
				}
			});
		}		
	}
	
	private void updateTaskResult(List<TaskProcessingEntry> entries) throws SQLException, InterruptedException {
		final List<Object[]> deleteOnSuccessRows=new ArrayList<>();
		final List<Object[]> postponedRows=new ArrayList<>();
		final List<Object[]> successUpdateRows=new ArrayList<>();
		final List<TaskRecord> childrenTasks=new ArrayList<>();
		final List<StatementBlock<Void>> statementBlocks=new ArrayList<>();
		final List<Object[]> errorUpdateRows=new ArrayList<>();	
		Map<TaskProcessingEntry,TaskState> entryToState=new HashMap<>();
		
		boolean needSignal=false;
		for (TaskProcessingEntry te : entries) {
			TaskRecord taskRecord = te.getTaskRecord();
			TaskType taskType = taskRecord.getTaskType();
			TaskHandlerProcessor taskHandlerProcessor = taskHandlerProcessors.get(taskType);
			if (te.processingState==TaskProcessingState.SUCCESS ) {
				if (taskHandlerProcessor.isDeleteOnSuccess()) deleteOnSuccessRows.add(new Object[] {taskType.getId(), taskRecord.getTicket()});
				else successUpdateRows.add(new Object[] {taskType.getId(), taskRecord.getTicket(),TaskState.SUCCESS.getStateId(), te.result ,AppEnv.getTime()});
				
				childrenTasks.addAll(te.childrenTasks);
				statementBlocks.addAll(te.statementBlocks);					
				
				entryToState.put(te, TaskState.SUCCESS);
				taskRecord.setTaskState(TaskState.SUCCESS);
				continue;
			} else if (te.processingState==TaskProcessingState.POSTPONED) {
				Long timeMs=te.postponeMs==null?AppEnv.getTime():te.postponeMs;
				needSignal=true;
				postponedRows.add(new Object[] {taskType.getId(), taskRecord.getTicket(), TaskState.INIT.getStateId(), timeMs, AppEnv.getTime()});
				continue;
			} else {
				// we got an error
				int newStateId=(taskRecord.getErrorCount()>=taskHandlerProcessor.maxErrorCount-1)?TaskState.ERROR.getStateId():TaskState.INIT.getStateId();
				if (newStateId==TaskState.INIT.getStateId())  {
					needSignal=true;
				} else {
					entryToState.put(te, TaskState.ERROR);
					taskRecord.setTaskState(TaskState.ERROR);
				}
				
				Long errorId=AppEnv.newId("task_queue_error_id");
				String msg=te.errorMsg;
				String error=te.error==null?null:Utils.getStackTrace(te.error);
				if (msg==null && te.error!=null) msg=te.error.getMessage();
				//                                          i1                 t1                i2                     i3              i4       i5        t2    t3                  
				errorUpdateRows.add(new Object[] {taskType.getId(), taskRecord.getTicket(), newStateId, taskRecord.getErrorCount()+1, te.lastMs, errorId, msg, error });
				
			}
		}
		db.commit(new StatementBlock<Void>() {
			int errorCount=0;
			@Override
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				deleteTasks(cw, deleteOnSuccessRows);
				updateTasks(cw, successUpdateRows);	
				postponeTasks(cw, postponedRows);
				errorTasks(cw, errorUpdateRows);
				
				if (!childrenTasks.isEmpty()) {
					cw.update("delete from common_tmp", true);
					taskInserter.insert(cw, childrenTasks);
				}
				for (StatementBlock<Void> sb : statementBlocks) {
					cw.update("delete from common_tmp", true);
					sb.execute(cw);
				}				
				
				if (cw.needsTempTableCleanup()) cw.update("delete from common_tmp", true);
				return null;
			}
			@Override
			public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
				if (ex!=null && ex.getMessage()!=null && ex.getMessage().toLowerCase().contains("deadlock")) {
					if (++errorCount < transactionRetryCount) {
						//System.err.println("ERROR UPDATING STATUS: "+errorCount+" : "+ex.getMessage());
						return false;
					}
					/*else {
						StringBuilder sb=new StringBuilder("Deadlock updating results for the following task/tickets:\n");
						for (TaskProcessingEntry te : entries) {
							TaskRecord taskRecord = te.getTaskRecord();
							TaskType taskType = taskRecord.getTaskType();
							sb.append("'"+taskType.getName()+"'/'"+taskRecord.getTicket()+"'\n");
						}
						AppEnv.logerr(sb.toString(), ex);
					}*/
				}
				return super.onError(cw, willAttemptToRetry, ex, start, now);
			}
		});
		if (needSignal) signalPickup();
		notifyListeners(entries);
	}

	private void deleteTasks(ConnectionWrap cw, List<Object[]> rows) throws SQLException, InterruptedException {
		if (rows.isEmpty()) return;
		else if (rows.size()>2) {
			if (cw.needsTempTableCleanup()) cw.update("delete from common_tmp", true);
			cw.batchInsert("insert into common_tmp (i1, t1) values (?, ?)",rows);
			for (String sql : new String[] {deleteTaskFieldNumSql,deleteTaskFieldTextSql,deleteTaskErrorSql, deleteTaskQueueSql, deleteTaskQueueProcessSql}) {
				cw.update(sql,true);						
			}
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
	protected void updateTasks(ConnectionWrap cw, List<Object[]> rows) throws SQLException, InterruptedException {
		if (rows.isEmpty()) return;
		else if (rows.size()>2) {
			cw.update("delete from common_tmp", true);
			// i1  - task_type_id, t1 - ticket, i2 task_state_id, b1 - result, i3 - last_ms
			cw.batchInsert("insert into common_tmp (i1, t1, i2, b1, i3) values (?, ?, ?, ?, ?)",rows);
			cw.update(updateTaskQueueStatusSql, true);	
			cw.update(deleteTaskQueueProcessSql,true);
		} else {
			for (Object[] row : rows) {
				//                                                    [2]       [3]        [4]                    [0]          [1]
				cw.update("update task_queue set task_state_id=?, result=?, last_ms=?   where task_type_id=? and ticket=?", true, row[2], row[3], row[4], row[0], row[1]);
				cw.update("delete from task_queue_process where task_type_id=? and ticket=?", true, row[0], row[1]);
			}
		}
	}
	protected void postponeTasks(ConnectionWrap cw, List<Object[]> rows) throws SQLException, InterruptedException {
		if (rows.isEmpty()) return;
		else if (rows.size()>2) {
			cw.update("delete from common_tmp", true);
			// i1  - task_type_id, t1 - ticket, i2 task_state_id, i3 - process_at_ms, i4 - last_ms
			cw.batchInsert("insert into common_tmp (i1, t1, i2, i3, i4) values (?, ?, ?, ?, ?)",rows);
			int cnt=cw.update(updateTaskQueuePostponeSql, true);	
			cnt=cw.update(deleteTaskQueueProcessSql,true);
			//i1  - task_type_id, t1 - ticket, i2 task_state_id, i3 - process_at_ms, i4 - last_ms			
		} else {
			for (Object[] row : rows) {
				//                                            [2]              [3]        [4]                    [0]          [1]
				int cnt=cw.update("update task_queue set task_state_id=?, process_at_ms=?, last_ms=?   where task_type_id=? and ticket=?", true, row[2], row[3], row[4], row[0], row[1]);
				cnt=cw.update("delete from task_queue_process where task_type_id=? and ticket=?", true, row[0], row[1]);
				//System.out.println("dropped "+row[0]+" - " + row[1]);
			}
		}
	}
	protected void errorTasks(ConnectionWrap cw, List<Object[]> rows) throws SQLException, InterruptedException {
		cw.update("delete from common_tmp", true);
		cw.batchInsert("insert into common_tmp (i1, t1, i2, i3, i4, i5, t2, t3) values (?, ?, ?, ?, ?, ?, ?, ?)",rows);
		cw.update(updateTaskQueueErrorSql, true);//ERROR: integer out of range
		cw.update("insert into task_queue_error (id, task_type_id, ticket, last_ms, message, error, system_process_id) select i5, i1, t1, i4, t2, t3, ? from common_tmp", true, AppEnv.systemProcessId());
		cw.update(deleteTaskQueueProcessSql,true);
	}
	
	Set<TaskCompletionListener> listeners=new HashSet<>();
	@Override
	public void addTaskCompletionListener(TaskCompletionListener lst) {
		if (lst!=null) synchronized (listeners) {
			listeners.add(lst);
		}	
	}
	private void notifyListeners(Collection<TaskProcessingEntry> entries) throws InterruptedException {
		synchronized (listeners) {
			for (TaskProcessingEntry entry : entries) {
				TaskRecord record = entry.getTaskRecord();
				if ( record.getTaskState()==TaskState.ERROR || record.getTaskState()==TaskState.SUCCESS || record.getTaskState()==TaskState.FATAL) {
					for (TaskCompletionListener l : listeners) {
						try {
							l.onTaskCompleted(record, entry.result);	
						} catch (Exception e) {
							e=Utils.proceedUnlessInterrupted(e);
							AppEnv.logerr("Failed to notify task completion for "+entry.getTaskRecord(), e);
						}
					}
				}
			}			
		}
	}
}
