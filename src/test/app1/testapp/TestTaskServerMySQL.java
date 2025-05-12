package testapp;


import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import appenv.env.AppEnv;
import appenv.task.TaskCompletionListener;
import appenv.task.TaskFuture;
import appenv.task.TaskQueueBackend;
import appenv.task.TaskQueueClient;
import appenv.task.TaskRecord;
import appenv.task.TaskType;
import appenv.util.JsonUtils;
import testapp.TestTaskServerPostgres.TestTaskCompletionListener;


public class TestTaskServerMySQL {

	public static void main(String[] strs) throws Exception {
		AppEnv.presetAppConfName("test-task-server");
		System.out.println(AppEnv.confName());
		System.out.println(JsonUtils.prettyPrint(AppEnv.conf()));
		
		TaskQueueBackend taskQueueServer = AppEnv.taskQueueBackend();

		
		taskQueueServer.addTaskCompletionListener(new TestTaskCompletionListener());
		
		Thread.sleep(10000000);
		AppEnv.destroy();
		//AppScope.getAppScope().destroy();
		
	}


	static class TestTaskCompletionListener implements TaskCompletionListener {

		boolean init=false, done=false;
		long lastReportTime, startTime;
		int totalCount=0;
		int intervalCount=0;
		int EXCPECTED_COUNT=100000;
		long REPORT_INTERVAL=10000;
		
		public synchronized void onTaskCompleted(TaskRecord taskRecord, byte[] result) {
			if (!init) init();
			long now=System.currentTimeMillis();
			++totalCount;
			if (now>=lastReportTime+REPORT_INTERVAL) {
				//report interval
				double sec=(now-lastReportTime)/1000.0;
				System.out.println("PROCESSESED "+intervalCount+" TASKS IN "+sec+" SECONDS, WITH RATE "+(intervalCount/sec)+ " TASKS/SECOND");
				
				intervalCount=0;
				lastReportTime=now;
			} else {
				++intervalCount;
			}
			if (totalCount>=EXCPECTED_COUNT && !done) {
				done=true;
				// report total
				double sec=(now-startTime)/1000.0;
				System.out.println("COMPLETED PROCESSING "+totalCount+" TASKS IN "+sec+" SECONDS WITH RATE "+(totalCount/sec)+ " TASKS/SECOND");
			}
		}

		private void init() {
			init=true;
			startTime=lastReportTime=System.currentTimeMillis();		
		}	
	}
	
}

