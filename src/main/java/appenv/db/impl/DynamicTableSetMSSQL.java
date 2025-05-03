/*
 * Alex Raybosh, 2009-2015
 */

package appenv.db.impl;

import java.sql.SQLException;

import appenv.db.ConnectionWrap;

public class DynamicTableSetMSSQL extends DynamicTableSetImpl {


	public DynamicTableSetMSSQL(String name) {
		super(name);
	}


	@Override
	public void init(DBImpl db) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public Table addTable(String name, String... fields) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Table getTable(String name) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public void insert(ConnectionWrap cw) throws SQLException,
			InterruptedException {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void delete(ConnectionWrap cw) throws SQLException,
			InterruptedException {
		// TODO Auto-generated method stub
		
	}

}
