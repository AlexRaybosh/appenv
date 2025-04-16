/*
 * 2009-2015, Alex Raybosh
 */

package appenv.db;

public interface BatchInputIterator {
	public boolean hasNext();
	public void next();
	public Object get(int idx);
	public void reset();
	public int getColumnCount();
}
