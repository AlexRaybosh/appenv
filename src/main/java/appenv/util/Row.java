/*
 * 2009-2015, Alex Raybosh
 */

package appenv.util;

import java.util.Arrays;
import java.util.TreeSet;

public class Row<T extends Comparable<T>> implements Comparable<Row<T>> {
	final T[] row;
	
	public Row(T... t) {
		if (t==null) throw new IllegalArgumentException("null row passed");
		row=t;
	}

	public T[] get() {
		return row;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(row);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Row<T> other = (Row<T>) obj;
		if (!Arrays.equals(row, other.row))
			return false;
		return true;
	}

	@Override
	public int compareTo(Row<T> o) {
		T[] otherRow=o.get();
		
		for (int i=0;i<row.length;i++) {
			T my=row[i];
			if (i>=otherRow.length) {
				//shorter is lower 
				return 1;
			}
			T other=otherRow[i];
			if (my==null && other!=null) return -1;
			if (other==null && my!=null) return 1;
			if (my!=null && other!=null) {
				int c=my.compareTo(other);
				if (c!=0) return c;
			}
		}
		return otherRow.length>row.length? -1 : 0;
	}


	@Override
	public String toString() {
		return "Row" + Arrays.toString(row);
	}

	public static void main(String[] args) {
		TreeSet<Row<String>> ordered=new TreeSet<Row<String>>();
		ordered.add(new Row<String>(new String[] {"B","A"}));
		ordered.add(new Row<String>(new String[] {"A","B"}));
		ordered.add(new Row<String>(new String[] {"C","A"}));
		ordered.add(new Row<String>(new String[] {"C",null,"X"}));
		ordered.add(new Row<String>(new String[] {"B","B"}));
		ordered.add(new Row<String>(new String[] {"B",null}));
		ordered.add(new Row<String>(new String[] {"B",""}));
		ordered.add(new Row<String>(new String[] {"B"}));
		ordered.add(new Row<String>(new String[0]));
		
		
		for (Row<String> t : ordered) System.out.println(t);
	}

}
