package test.db;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import appenv.db.ConnectionWrap;
import appenv.db.DB;
import appenv.db.StatementBlock;
import appenv.db.TableName;



public class T0 {

	public static void main(String[] args) throws Exception {
		DB db=DB.create("jdbc:mysql://localhost:3306/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
		//DB test.db=DB.create("jdbc:mysql://192.168.1.2:6666/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
		//DB test.db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "business", "business");
//		DB test.db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "model", "oracle");
		//test.db.setBatchSize(2);
		TableName tn = db.getTableName("aa.bbb");
		System.out.println(tn.toString(db));
		/*String msg="ORA-01950: no Privileges on tablespace 'DATA_TBL'";
		System.out.println(msg.matches("(?i:.*no\\s+privileges\\s+on\\s+tablespace.*)"));
		msg="\nORA-01918: uer 'CONF1' does not exist\n";
		System.out.println(Pattern.compile("(?i:.*user\\s+.+\\s+does not exist.*$)", Pattern.DOTALL).matcher(msg).matches());
		*/
		System.out.println("done");
	}

}
