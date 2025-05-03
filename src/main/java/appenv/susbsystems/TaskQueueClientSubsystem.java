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
import appenv.db.DB;
import appenv.db.StatementBlock;
import appenv.env.AppEnv;
import appenv.env.SubSystem;
import appenv.task.TaskFuture;
import appenv.task.TaskInserter;
import appenv.task.TaskQueueClient;
import appenv.task.TaskRecord;
import appenv.task.TaskState;
import appenv.util.JsonUtils;
import appenv.util.UnorderedRow;

public class TaskQueueClientSubsystem extends SubSystem implements TaskQueueClient {
	JsonObject conf;
	DB db;
	TaskInserter taskInserter;
	@Override
	public boolean init(boolean initial, JsonObject c) throws Exception {
		this.conf=c;
		//System.out.println(JsonUtils.prettyPrint(conf));
		db=AppEnv.db(JsonUtils.getString("core", conf, "database"));
		taskInserter=new TaskInserter(db, conf);
		return true;
	}

	@Override
	public void destroy() {
	}

	@Override
	public boolean tick(long startAfterMs, long intervalMs, String tickName, Long lastRun) throws Exception {
		return false;
	}

	@Override
	public TaskFuture submit(TaskRecord task) throws InterruptedException {
		return taskInserter.submit(task);
	}
	
	
	@Override
	public void insert(ConnectionWrap cw, Collection<TaskRecord> records) throws SQLException, InterruptedException {
		taskInserter.insert(cw, records);
	}

}
