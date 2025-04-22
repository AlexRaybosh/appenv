package appenv.susbsystems;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Future;

import com.google.gson.JsonObject;

import appenv.async.AsyncEngine;
import appenv.async.Request;
import appenv.async.Service;
import appenv.async.ServiceBackend;
import appenv.db.ConnectionWrap;
import appenv.db.StatementBlock;
import appenv.env.AppEnv;
import appenv.env.SubSystem;
import appenv.task.TaskFuture;
import appenv.task.TaskQueueClient;
import appenv.task.TaskRecord;
import appenv.task.TaskState;
import appenv.util.JsonUtils;
import appenv.util.UnorderedRow;

public class TaskQueueClientSubsystem extends SubSystem implements TaskQueueClient {
	JsonObject conf;
	AsyncEngine asyncEngine;
	Service<Long> lookupCreateService;
	String dbName;
	@Override
	public boolean init(boolean initial, JsonObject c) throws Exception {
		this.conf=c;
		System.out.println(JsonUtils.prettyPrint(conf));
		if (AppEnv.envTypeId()==null) throw new RuntimeException("Environment "+AppEnv.envTypeName()+"' is not registered in the DB");
		asyncEngine=AsyncEngine.create();
		lookupCreateService=asyncEngine.register("lookupCreateByName", new ServiceBackend<Long>() {	
			public void process(List<Request<Long>> bulk) throws Exception {insert(bulk);}
			public int getMaxWorkers() {return JsonUtils.getInteger(1, conf, "insertConcurrency");}
			public int getMaxQueuedRequests() {return JsonUtils.getInteger(1, conf, "insertQueueSize");}
			public int getMaxBulkSize() {
				return JsonUtils.getInteger(1, conf, "insertBulkSize");
			}
		});
		dbName=JsonUtils.getString("core", conf, "database");
		return true;
	}

	protected void insert(List<Request<Long>> bulk) throws SQLException, InterruptedException {
		final Map<TaskRecord,List<Request<Long>>> map=new HashMap<>(bulk.size());  
		for (Request<Long> r : bulk) {
			TaskRecord task=(TaskRecord)r.getArgs()[0];
			List<Request<Long>> lst = map.get(task);
			if (lst==null) {lst=new ArrayList<>(1);	map.put(task, lst);}
			lst.add(r);
		}
		AppEnv.db(dbName).commit(new StatementBlock<Void>() {
			int retryCount=0;
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				insert(cw, map.keySet());
				return null;				
			}
			@Override
			public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
				if (retryCount++ < JsonUtils.getInteger(3, conf, "insertRetryOnErrorCount")) return false;
				return super.onError(cw, willAttemptToRetry, ex, start, now);
			}
		});
		
		for (Entry<TaskRecord, List<Request<Long>>> e : map.entrySet()) {
			TaskRecord task=e.getKey();
			for (Request<Long> r : e.getValue()) {
				r.setResult(task.getId());
			}
		}
		
	}

	@Override
	public void destroy() {
	}

	@Override
	public boolean tick(String tickName, Long lastRun) throws Exception {
		return false;
	}

	@Override
	public TaskFuture submit(TaskRecord task) throws InterruptedException {
		Future<Long> ret = lookupCreateService.call(asyncEngine, task);
		return new TaskFuture(task.getTicket(), ret);
	}
	
	
	@Override
	public void insert(ConnectionWrap cw, Collection<TaskRecord> records) throws SQLException, InterruptedException {
		
		Map<UnorderedRow<Object>, List<TaskRecord>> ticketMap=new HashMap<>();
		for (TaskRecord record : records) {
			UnorderedRow<Object> key=new UnorderedRow<>(record.getTaskTypeId(), record.getTicket());
			List<TaskRecord> subList = ticketMap.get(key);
			if (subList==null) {
				subList=new ArrayList<>(1);
				ticketMap.put(key, subList);
			}
			subList.add(record);
		}
		
		if (cw.needsTempTableCleanup()) cw.update("delete from common_tmp", true);
		
		cw.batchInsertUnorderedRows("insert into common_tmp (i1, t1) values (?, ?)",ticketMap.keySet());
		
		String sql="select t.i1 as task_type_id, t.t1 as ticket, q.id  from common_tmp t straight_join task_queue q FORCE INDEX (task_ticket_idx) on (t.i1=q.task_type_id and cast(t.t1 as char)=q.ticket)";
		//String sql="select t.i1 as task_type_id, t.t1 as ticket, q.id  from common_tmp t straight_join task_queue q FORCE INDEX (task_ticket_idx) on (t.i1=q.task_type_id and t.t1=q.ticket)";
		/*String esql="EXPLAIN PARTITIONS "+sql;
		for ( Map<String, Object> row : cw.selectLabelMap(esql, false)) {
			//for (Entry<String, Object> e : row.entrySet()) {
			//	System.out.println(e.getKey() +"\t:\t"+e.getValue());
			//}
			//for (Object c : row) System.out.print(c+"\t|\t");
			System.out.println(row);
		}
		System.out.println("------------------------------------------------------------------");
		*/
		for (Object[] row : cw.select(sql, true)) {
			Number id = (Number)row[2];
			UnorderedRow<Object> key=new UnorderedRow<>(((Number)row[0]).intValue(), row[1]);
			List<TaskRecord> subList = ticketMap.remove(key);
			if (subList!=null) {
				// duplicate task
				for (TaskRecord task : subList) task.setId(id.longValue());
			}
		}
		List<Object[]> taskRows=new ArrayList<>();
		List<Object[]> fieldTextRows=new ArrayList<>();
		List<Object[]> fieldNumRows=new ArrayList<>();
		Long now=System.currentTimeMillis();
		for (Entry<UnorderedRow<Object>, List<TaskRecord>> e : ticketMap.entrySet()) {
			List<TaskRecord> subList = e.getValue();
			Long id=AppEnv.newId("task_queue_id");
			for (TaskRecord task : subList) task.setId(id);
			TaskRecord task=subList.get(0); // only interest 1 task for the key
			long expireMs=task.getExpireMs();
			Object[] taskRow=new Object[] {
					task.getId(), 
					task.getTaskTypeId(), 
					TaskState.INIT.getStateId(),
					AppEnv.envTypeId(),
					task.getTicket(),
					task.getProcessAtMs(),
					task.getPayload(),
					now,
					expireMs,
					0,
					now
					};
			taskRows.add(taskRow);
			if (task.getFieldToText()!=null) {
				for (Entry<String, Set<String>> te : task.getFieldToText().entrySet()) {
					String field=te.getKey();
					for (String v : te.getValue()) {
						Long tid=AppEnv.newId("task_field_text_id");
						fieldTextRows.add(new Object[] {tid, id, task.getTaskTypeId(), field, v});
					}
				}
			}
			if (task.getFieldToNumber()!=null) {
				for (Entry<String, Set<Long>> te : task.getFieldToNumber().entrySet()) {
					String field=te.getKey();
					for (Long v : te.getValue()) {
						Long tid=AppEnv.newId("task_field_num_id");
						fieldNumRows.add(new Object[] {tid, id, task.getTaskTypeId(), field, v});
					}
				}
			}

		}
		if (!taskRows.isEmpty()) {
			String insertTaskSql="insert into task_queue (id,task_type_id,task_state_id,env_type_id,"
					+ "ticket,process_at_ms,payload,insert_ms,"
					+ "expire_ms, error_count, last_ms) values ("
					+ "?,?,?,?, ?,?,?,?, ?,?,?)";
			cw.batchInsert(insertTaskSql, taskRows);

			if (!fieldTextRows.isEmpty()) {
				String insertTaskFieldTextSql="insert into task_field_text (id,task_queue_id,task_type_id,field,value) values (?,?,?,?,?)";
				cw.batchInsert(insertTaskFieldTextSql, fieldTextRows);
			}
			
			if (!fieldNumRows.isEmpty()) {
				String insertTaskFieldNumSql="insert into task_field_num (id,task_queue_id,task_type_id,field,value) values (?,?,?,?,?)";
				cw.batchInsert(insertTaskFieldNumSql, fieldNumRows);
			}
		}	
		if (cw.needsTempTableCleanup()) cw.update("delete from common_tmp", true);
	}

	
	

}
