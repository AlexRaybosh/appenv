/*
 * Alex Raybosh, 2009-2015
 */

package appenv.db;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

public interface LabelRowHandler extends RowHandler {
	void onRow(ResultSetMetaData md, Map<String,Object> row) throws SQLException, InterruptedException;
}
