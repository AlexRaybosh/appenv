/*
 * 2009-2015, Alex Raybosh
 */

package appenv.db;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public interface ArrayRowHandler extends RowHandler {
	void onRow(ResultSetMetaData md, Object[] row) throws SQLException, InterruptedException;
}