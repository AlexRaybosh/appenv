package appenv.susbsystems;

import com.google.gson.JsonObject;

import appenv.env.SubSystem;
import appenv.task.TaskQueueBackend;

public class TaskQueueBackendSubsystem extends SubSystem implements TaskQueueBackend {

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
