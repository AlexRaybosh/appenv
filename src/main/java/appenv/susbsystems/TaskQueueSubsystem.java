package appenv.susbsystems;

import com.google.gson.JsonObject;

import appenv.env.SubSystem;
import appenv.task.TaskQueue;

public class TaskQueueSubsystem extends SubSystem implements TaskQueue {

	@Override
	public boolean init(boolean initial, JsonObject conf) throws Exception {

		return true;
	}

	@Override
	public void destroy() {
	}

	@Override
	public boolean tick(String tickName, Long lastRun) throws Exception {
		return false;
	}

}
