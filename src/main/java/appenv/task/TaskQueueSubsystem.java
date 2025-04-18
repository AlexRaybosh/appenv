package appenv.task;

import com.google.gson.JsonObject;

import appenv.env.SubSystem;

public class TaskQueueSubsystem extends SubSystem implements TaskQueue {

	@Override
	public boolean init(boolean initial, JsonObject conf) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean tick(String tickName, Long lastRun) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

}
