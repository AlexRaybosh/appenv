/*
 * 2009-2015, Alex Raybosh
 */

package appenv.db.impl;

import java.sql.SQLException;

import appenv.db.DynamicTableSet;

public abstract class DynamicTableSetImpl extends DynamicTableSet {
	final protected String name;
	
	public DynamicTableSetImpl(String name) {
		this.name=name;
	}
	
	@Override
	public String getName() {
		return name;
	}


	public abstract void init(DBImpl db) throws InterruptedException, SQLException;

}
