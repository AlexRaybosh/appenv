package test.db;


import appenv.db.DB;






public class T6 {

	public static void main(String[] args) throws Exception {
		//DB test.db=DB.create("jdbc:mysql://localhost:3306/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
		//DB test.db=DB.create("jdbc:mysql://192.168.1.2:6666/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true", "business", "business");
		//DB test.db=DB.create("jdbc:mysql://192.168.1.2:6666/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
		DB db=DB.create("jdbc:mysql://localhost:3306/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
//		DB test.db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "business", "business");
//		DB test.db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "model", "oracle");
		//test.db.setBatchSize(2);
		
		/*for (Object[] row : test.db.select("select * from workdb.t1 limit 1000",true)) {
			System.out.println(Utils.csvList(row));
		}
		for (Map<String, Object> row : test.db.selectLabelMap("select * from workdb.t1 limit 1000",true)) {
			System.out.println(row);
		}
		test.db.select("select * from workdb.t1 limit 1000", true, new ArrayRowHandler() {
			public void onError() throws InterruptedException {}
			public void onRow(ResultSetMetaData md, Object[] row) {
				System.out.println(Utils.csvList(row));
			}
		});
		test.db.select("select * from workdb.t1 limit 1000", true, new LabelRowHandler() {
			public void reset() throws InterruptedException {}
			public void onRow(ResultSetMetaData md, Map<String, Object> row)  {
				System.out.println(row);
			}
		});
		test.db.select("select * from workdb.t1 limit 1000", true, new ResultSetHandler() {
			public void reset() throws InterruptedException {}
			public void onRow(ResultSet rs) throws SQLException, InterruptedException {
				System.out.println(""+rs.getObject(1).getClass()+", "+rs.getObject(2).getClass());
		}});
		*/
		db.addInitSqlWithCleanup(false,"create temporary table X (id unsigned int) engine=memory", null);
	}

}
