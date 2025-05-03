package appenv.task;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;

import com.google.gson.JsonObject;

import appenv.async.AsyncEngine;
import appenv.async.Request;
import appenv.async.Service;
import appenv.async.ServiceBackend;
import appenv.db.ConnectionWrap;
import appenv.db.DB;
import appenv.db.StatementBlock;
import appenv.env.AppEnv;
import appenv.util.JsonUtils;
import appenv.util.UnorderedRow;
import appenv.util.Utils;

public class TaskReader {
	final JsonObject conf;
	final AsyncEngine asyncEngine;
	final DB db;
	final Service<TaskRecord> taskReaderService;
	String readTaskQueueSql;
	String readTaskTextFieldSql;
	String readTaskNumFieldSql;
	
	public TaskReader(DB db, JsonObject conf) throws Exception {
		this.conf=conf;
		this.db=db;
		//System.out.println(JsonUtils.prettyPrint(conf));
		if (AppEnv.envTypeId()==null) throw new RuntimeException("Environment "+AppEnv.envTypeName()+"' is not registered in the DB");
		asyncEngine=AsyncEngine.create();
		taskReaderService=asyncEngine.register("reader", new ServiceBackend<TaskRecord>() {	
			public void process(List<Request<TaskRecord>> bulk) throws Exception {read(bulk);}
			public int getMaxWorkers() {return JsonUtils.getInteger(1, conf, "readConcurrency");}
			public int getMaxQueuedRequests() {return JsonUtils.getInteger(64, conf, "readLookupQueueSize");}
			public int getMaxBulkSize() {return JsonUtils.getInteger(16, conf, "readBulkSize");}
		});
		
		readTaskQueueSql="select task_type_id, ticket, task_state_id, env_type_id, process_at_ms, payload, insert_ms from common_tmp t join task_queue q on (t.i1=q.task_type_id and cast(t.t1 as char)=q.ticket)";
		readTaskQueueSql=JsonUtils.getString(readTaskQueueSql, conf, "readTaskQueueSql");
		
		readTaskTextFieldSql="select task_type_id, ticket, field, value from common_tmp t join task_field_text f on (t.i1=f.task_type_id and cast(t.t1 as char)=f.ticket)";
		readTaskTextFieldSql=JsonUtils.getString(readTaskTextFieldSql, conf, "readTaskTextFieldSql");

		readTaskNumFieldSql="select task_type_id, ticket, field, value from common_tmp t join task_field_num f on (t.i1=f.task_type_id and cast(t.t1 as char)=f.ticket)";
		readTaskNumFieldSql=JsonUtils.getString(readTaskNumFieldSql, conf, "readTaskNumFieldSql");
	}

	
	public final Future<TaskRecord> lookup(TaskType taskType, String ticket) throws InterruptedException {
		return taskReaderService.call(asyncEngine, taskType.getId(), ticket);
	}
	
	private void read(List<Request<TaskRecord>> bulk) throws SQLException, InterruptedException {
		final Map<UnorderedRow<Object>,List<Request<TaskRecord>>> keyToRequests=new HashMap<>(bulk.size());  
		for (Request<TaskRecord> r : bulk) {
			UnorderedRow<Object> key=new UnorderedRow<Object>(r.arg(0), r.arg(1));
			List<Request<TaskRecord>> lst = keyToRequests.get(key);
			if (lst==null) {lst=new ArrayList<>(1);	keyToRequests.put(key, lst);}
			lst.add(r);
		}
		db.commit(new StatementBlock<Void>() {
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				if (cw.needsTempTableCleanup()) cw.update("delete from common_tmp", true);
				cw.batchInsertUnorderedRows("insert into common_tmp (i1, t1) values (?, ?)",keyToRequests.keySet());
				Map<UnorderedRow<Object>,TaskRecord> keyToTaskRecord=new HashMap<>(bulk.size());  
				for (Map<String, Object> row : cw.selectLabelMap(readTaskQueueSql, true)) {
					TaskType taskType = TaskType.id(Utils.toInt(row.get("task_type_id")));
					String ticket=Utils.toString(row.get("ticket"));
					TaskState taskState=TaskState.id(Utils.toInt(row.get("task_state_id")));
					int envId=Utils.toInt(row.get("env_type_id"));
					long processMs=Utils.toLong(row.get("process_at_ms"));
					byte[] payload=Utils.toByteArray(row.get("payload"));
					long insertMs=Utils.toLong(row.get("insert_ms"));
					TaskRecord taskRecord=new TaskRecord(taskType, ticket, payload);
					taskRecord.setTaskState(taskState);
					taskRecord.setEnvId(envId);
					taskRecord.setProcessAtMs(processMs);
					taskRecord.setInsertedAtMs(insertMs);
					UnorderedRow<Object> key=new UnorderedRow<Object>(taskType.getId(), ticket);
					keyToTaskRecord.put(key, taskRecord);
				}
				for (Map<String, Object> row : cw.selectLabelMap(readTaskTextFieldSql, true)) {
					TaskType taskType = TaskType.id(Utils.toInt(row.get("task_type_id")));
					String ticket=Utils.toString(row.get("ticket"));
					String field=Utils.toString(row.get("field"));
					String value=Utils.toString(row.get("value"));
					UnorderedRow<Object> key=new UnorderedRow<Object>(taskType.getId(), ticket);
					TaskRecord taskRecord = keyToTaskRecord.get(key);
					if (taskRecord!=null) taskRecord.addPropString(field, value);
				}
				for (Map<String, Object> row : cw.selectLabelMap(readTaskNumFieldSql, true)) {
					TaskType taskType = TaskType.id(Utils.toInt(row.get("task_type_id")));
					String ticket=Utils.toString(row.get("ticket"));
					String field=Utils.toString(row.get("field"));
					Long value=Utils.toLong(row.get("value"));
					UnorderedRow<Object> key=new UnorderedRow<Object>(taskType.getId(), ticket);
					TaskRecord taskRecord = keyToTaskRecord.get(key);
					if (taskRecord!=null) taskRecord.addPropNumber(field, value);					
				}
				for (Entry<UnorderedRow<Object>, List<Request<TaskRecord>>> e : keyToRequests.entrySet()) {
					UnorderedRow<Object> key=e.getKey();
					List<Request<TaskRecord>> requests = e.getValue();
					TaskRecord taskRecord=keyToTaskRecord.get(key);
					for (Request<TaskRecord> r : requests) {
						r.setResult(taskRecord);
					}
				}
				if (cw.needsTempTableCleanup()) cw.update("delete from common_tmp", true);
				return null;				
			}
		});
		
	}
}
