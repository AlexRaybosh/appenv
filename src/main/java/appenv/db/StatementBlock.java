/*
 * 2009-2015, Alex Raybosh
 */

package appenv.db;

import java.sql.SQLException;

public abstract class  StatementBlock<Ret> {
	/**
	 * Block of SQL operations combined together. Can be executed as part of a transaction {@link DB#commit(StatementBlock)} method,
	 * Or as series of SQL operations in autocommit context {@link DB#autocommit(StatementBlock)} method
	 * @param cw
	 * @return
	 * @throws SQLException
	 * @throws InterruptedException
	 */
	public abstract Ret execute(ConnectionWrap cw) throws SQLException, InterruptedException;
	
	/**
	 * Signals that SQL Exception occurred during execution
	 * @return true - follow the default retry logic (if execution time exceeds timeout, throw the exception, if not, sleep, then retry ), 
	 * false - ignore the default retry logic (keeps spinning, unless re-throws exception)
	 * @throws SQLException to immediately fail.   
	 */
	public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
		return true;
	}
	public String getDebugString() {return "";}
}
