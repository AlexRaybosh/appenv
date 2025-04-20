package appenv.task;

import java.sql.SQLException;
import java.util.Collection;

import com.google.gson.JsonObject;

import appenv.db.ConnectionWrap;
import appenv.env.AppEnv;

public interface TaskQueueClient {

	TaskFuture submit(TaskRecord task) throws InterruptedException;
	
	default TaskFuture submit(TaskType tt, String ticket, byte[] payload, JsonObject props) throws InterruptedException {
		if (ticket==null) ticket=AppEnv.createUniqueKey();
		TaskRecord task = new TaskRecord(tt,ticket,payload,props);
		return submit(task);
	}
	default TaskFuture submit(TaskType tt, JsonObject props, byte[] payload) throws InterruptedException {
		return submit(tt,null,payload,props);
	}

	/*
	 * insert as part of an outer transaction
	 */
	void insert(ConnectionWrap cw, Collection<TaskRecord> tasks) throws SQLException, InterruptedException;
}
