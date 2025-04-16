/*
 * 2009-2015, Alex Raybosh
 */

package appenv.db.impl;

import java.sql.SQLException;

public interface Profiler {

	Object onStart(Object conId, String sql, Object[] args);

	void onEnd(Object ticket, boolean success, SQLException sex);

	void setConnectionInfo(Object ticket, Object conId);

}
