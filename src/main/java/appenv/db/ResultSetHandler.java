/*
 * 2009-2015, Alex Raybosh
 */

package appenv.db;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface ResultSetHandler extends RowHandler {
	void onRow(ResultSet rs) throws SQLException, InterruptedException;
}
