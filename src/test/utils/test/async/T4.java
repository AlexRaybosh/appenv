package test.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import appenv.async.AsyncEngine;
import appenv.async.Request;
import appenv.async.Result;
import appenv.async.ServiceBackend;

public class T4 {

	public static void main(String[] args) throws Exception {
		AsyncEngine engine=AsyncEngine.create();
		engine.register("MySquare", createBackend1());
		
		List<Result> results=new ArrayList<>();
		for (int i=0;i<100;++i) {
			Result r=engine.call("MySquare", i);
			//Result r=engine.callNoLimit("MySquare", i);
			results.add(r);
		}
		long soFarSubmitted=engine.last();
		
		System.out.printf("--> Sending done signal, #submitted=%d,  #completed=%d\n",soFarSubmitted,engine.getNumberOfCompletedCalls());
		
		/*for (Result r : results) {
			Object arg=r.getArgs()[0];			
			System.out.println(arg+" -> "+r.get());
		}
		*/
		Future<Long> end = engine.completeLast();		
		System.out.println("<-- Done processing #requests="+end.get());
//		engine.shutdown();

	}

	private static ServiceBackend<Integer> createBackend1() {

		ServiceBackend<Integer> backend=new ServiceBackend<Integer>() {
			@Override
			public void process(List<Request<Integer>> bulk) throws Exception {
				String threadName=Thread.currentThread().getName();
				StringBuilder sb=new StringBuilder(threadName+" : ");
				for (Request<Integer> r : bulk) {
					Integer num=(Integer)r.getArgs()[0];
					r.setResult(num*num);
					sb.append("\tN-").append(num);
				}
				System.out.println(sb);
				Thread.sleep(500); // <-- simulate IO
				
			}
			
			@Override
			public int getMaxBulkSize() {
				return 3;
			}

			@Override
			public long getWorkerReleaseTimeout() {return 1;}

			@Override
			public int getMaxWorkers() {return 3;}

			@Override
			public int getMaxQueuedRequests() {return 10;}
		};
		return backend;
	}

}
