package test.async;

import java.util.ArrayList;
import java.util.List;

import appenv.async.AsyncEngine;
import appenv.async.Request;
import appenv.async.Result;
import appenv.async.ServiceBackend;

public class T1 {

	public static void main(String[] args) throws Exception {
		AsyncEngine engine=AsyncEngine.create();
		engine.register("MySquare", createMySquareBackend());
		
		List<Result<Integer>> results=new ArrayList<>();
		for (int i=0;i<20;++i) {
			Result<Integer> r=engine.call("MySquare", i);
			results.add(r);
		}
		
		Thread.sleep(500);
		
		for (Result<Integer> r : results) {
			Object arg=r.getArgs()[0];			
			System.out.println(arg+"*"+arg+" = "+r.get());
		}


	}

	private static ServiceBackend<Integer> createMySquareBackend() {

		ServiceBackend<Integer> backend=new ServiceBackend<Integer>() {
			@Override
			public void process(List<Request<Integer>> bulk) throws Exception {
				String threadName=Thread.currentThread().getName();
				StringBuilder sb=new StringBuilder(threadName+" : ");
				for (Request<Integer> r : bulk) {
					Integer num=(Integer)r.getArgs()[0];
					r.setResult(num*num);
					sb.append("\tA=").append(num);
				}
				System.out.println(sb);
				Thread.sleep(500);// <-- simulate IO
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
