/*
 * Alex Raybosh, 2009-2015
 */

package appenv.db;

import java.sql.SQLException;

public abstract class DynamicTableSet {

	public abstract Table addTable(String name,String... fields);
	public abstract Table getTable(String name);
	
	public interface Table {
		public void add(Object... vals);
		void getSelectSQL(StringBuilder sb);
		public String getSelectSQL();
		public void getJoinableSelectSQL(StringBuilder sb);
		public String getJoinableSelectSQL();
		
	}

	public abstract void insert(ConnectionWrap cw) throws SQLException, InterruptedException;
	public abstract void delete(ConnectionWrap cw) throws SQLException, InterruptedException;
	public abstract String getName();
	

}
