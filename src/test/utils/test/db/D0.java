package db;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import appenv.async.AsyncEngine;
import appenv.async.CompletionCallback;
import appenv.async.Request;
import appenv.async.ServiceBackend;
import appenv.async.Workload;
import appenv.db.BatchInputIterator;
import appenv.db.ConnectionWrap;
import appenv.db.DB;
import appenv.db.DBID;
import appenv.db.StatementBlock;
import appenv.util.EncodingUtils;




public class D0 {

	public static void main(String[] args) throws Exception {
		DB db=DB.create("jdbc:mysql://localhost:3306/test2?allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true&useSSL=false", "test2", "");
		//		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "business", "business");		
		AsyncEngine engine=AsyncEngine.create();
		engine.register("shoveIt", createShoveItBackend(db));
		
		try {

			DBID dbid=new DBID(db,"seq");

			db.update("drop table if exists  d1_uuid ");
			db.update("create table d1_uuid ("
					+ "doc_id int unsigned not null,"
					+ "sec_id int unsigned not null,"
					+ "uuid binary(16) not null,"
					+ "version_from int unsigned not null,"
					+ "version_to int unsigned not null,"
					+ "primary key(doc_id,sec_id) "
					//+ ",unique index (uuid)"
					+") engine=innodb"
					);

			db.setBatchSize(4096);
			
			CompletionCallback<Void> callback=new CompletionCallback<Void>() {
				public void completed(Workload workload, Void ret,Object[] args) {}
				public void errored(Workload workload, Throwable e,	Object[] args) {
					e.printStackTrace();
					System.exit(1);
				}
			};
			
			for (long i=0;i<1000000;++i) {
				long doc_id=dbid.next("doc_id");
				long ts_sec=System.currentTimeMillis()/1000l;
				for (int s=0;s<10;++s) {
					long sec_id=dbid.next("sec_id");
					
					engine.<Void>callWithCallback("shoveIt",callback,  doc_id, sec_id, ts_sec);
				}
			}

			
			
			long last=engine.last();
			System.out.println("Last: "+last);
			long complete=engine.completeLast().get();
			System.out.println("Complete: "+complete);
			System.out.println("done");
			
		} finally {
			db.close();
			se.shutdownNow();
		}
	}

	private static ServiceBackend<Void> createShoveItBackend(final DB db) {
		ServiceBackend<Void> backend=new ServiceBackend<Void>() {
			public void process(final List<Request<Void>> bulk) throws Exception {
				//String threadName=Thread.currentThread().getName();
				//System.out.println(threadName+" : "+bulk.size());
				db.commit(new StatementBlock<Void>() {
					public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
						
						cw.	batchInsert(
							"insert into d1_uuid (doc_id,sec_id,version_from,uuid,version_to) values (?,?,?,?,4294967295)", 
							new BatchInputIterator() {
							Iterator<Request<Void>> it=bulk.iterator();
							Object[] row;
							public boolean hasNext() {return it.hasNext();}
							public void next() {row=it.next().getArgs();}
							public Object get(int idx) {return idx<3? row[idx] : EncodingUtils.uuidToByteArray(UUID.randomUUID());}
							public void reset() {it=bulk.iterator();}
							public int getColumnCount() {return 4;}
							
						});
						
						
						return null;
					}
				});
				reportAdd(bulk.size());
			}
			public int getMaxBulkSize() {return db.getBatchSize();}
			public long getWorkerReleaseTimeout() {return 30000;}
			public int getMaxWorkers() {return db.getMaxConnections();}
			public int getMaxQueuedRequests() {return 100000;}
		};
		return backend;
	}
	
	static ScheduledExecutorService se=Executors.newSingleThreadScheduledExecutor();
	static {
		se.scheduleWithFixedDelay(new Runnable(){
			public void run() {
				if (cnt.get()>0) {
					long num=cnt.get();
					long delta=num-prevNum;
					nextTs=System.currentTimeMillis();
				
					float secs=(nextTs-prevTs)/1000f;
					if (secs>0) { 
						float speed=delta/secs;
						System.out.printf("shoved %d records, speed %.02f rec a second\n", num, speed);
					
						prevTs=nextTs;
						prevNum=num;
					}

				}
			}
		}, 0, 2, TimeUnit.SECONDS);
	}
	static AtomicLong cnt=new AtomicLong();
	static long prevTs=System.currentTimeMillis(), nextTs=-1, prevNum;

	private static void reportAdd(int size) {
		long num=cnt.addAndGet(size);
	}
}
