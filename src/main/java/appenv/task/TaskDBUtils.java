package appenv.task;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonObject;

import appenv.db.ConnectionWrap;
import appenv.db.StatementBlock;
import appenv.env.AppEnv;
import appenv.util.JsonUtils;
import appenv.util.UnorderedRow;

public class TaskDBUtils {

	public void insert(ConnectionWrap cw, List<TaskRecord> records) throws SQLException, InterruptedException {
		if (AppEnv.envId()==null) throw new RuntimeException("Environment '"+AppEnv.envName()+"' is not registered in the DB");
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
		String sql="select q.task_type_id, q.ticket, q.id  from common_tmp t join task_queue q on (t.i1=q.task_type_id and t.t1=q.ticket)";
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
			UnorderedRow<Object> key = e.getKey();
			List<TaskRecord> subList = e.getValue();
			Long id=AppEnv.newId("task_queue_id");
			for (TaskRecord task : subList) task.setId(id);
			TaskRecord task=subList.get(0); // only interest 1 task for the key
			long expireMs=task.getExpireMs();
			Object[] taskRow=new Object[] {
					task.getId(), 
					task.getTaskTypeId(), 
					TaskState.INIT.getStateId(),
					AppEnv.envId(),
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
			String insertTaskSql="insert into task_queue (id,task_type_id,task_state_id,env_id,"
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
	
	
	public static void main(String[] args) throws Exception {
		String sample="{\n"
		+ "      \"url\": \"zzzzzzzzzz\",\n"
		+ "      \"allowOverborrow\": true,\n"
		+ "      \"maxCachedPreparedStatements\": 2000.1,\n"
		+ "      \"maxConnections\": 30,\n"
		+ "      \"retryTimeoutMilliseconds\": 10000,\n"
		+ "      \"initStatements\": [\n"
		+ "          \"onOpen\",  \"onClose\""
		+ "      ]\n"
		+ "    }";
		JsonObject props = JsonUtils.parseJsonObject(sample);
		List<TaskRecord> records=new ArrayList<>();
		records.add(new TaskRecord(TaskType.id(0),"a","payload1".getBytes(),props));
		records.add(new TaskRecord(TaskType.id(0),"b",props));
		TaskDBUtils utils=new TaskDBUtils();
		AppEnv.db().commit(new StatementBlock<Void>() {
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				utils.insert(cw, records);
				return null;
				
			}
		});
	}

}
