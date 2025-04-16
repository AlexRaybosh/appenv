/*
 * 2009-2015, Alex Raybosh
 */

package appenv.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 
 * @author Alex Raybosh
 * The class represents a row of elements, as a thin wrapper on top of an array (e.g. a record), suitable for identity comparison based on identity of the individual elements
 * @param <T>
 */
public class UnorderedRow<T>  {
	final T[] row;
	/**
	 * Construct a row based on an array of elements
	 * @param t
	 */
	public UnorderedRow(T... t) {
		if (t==null) throw new IllegalArgumentException("null row passed");
		row=t;
	}
	/**
	 * 
	 * @return array of corresponding elements
	 */
	final public T[] get() {
		return row;
	}
	
	final public int size() {
		return row.length;
	}
	final public void set(int i, T val) {
		row[i]=val;
	}

	/* 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(row);
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UnorderedRow<T> other = (UnorderedRow<T>) obj;
		if (!Arrays.equals(row, other.row))
			return false;
		return true;
	}


	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "UnorderedRow" + Arrays.toString(row);
	}

	/**
	 * Simple demo of the class
	 * @param args
	 */
	public static void main(String[] args) {
		Set<UnorderedRow<String>> ordered=new HashSet<UnorderedRow<String>>();
		ordered.add(new UnorderedRow<String>(new String[] {"B","A"}));
		ordered.add(new UnorderedRow<String>(new String[] {"A","B"}));
		ordered.add(new UnorderedRow<String>(new String[] {"C","A"}));
		ordered.add(new UnorderedRow<String>(new String[] {"C",null,"X"}));
		ordered.add(new UnorderedRow<String>(new String[] {"B","B"}));
		ordered.add(new UnorderedRow<String>(new String[] {"B",null}));
		ordered.add(new UnorderedRow<String>(new String[] {"B",""}));
		ordered.add(new UnorderedRow<String>(new String[] {"B"}));
		ordered.add(new UnorderedRow<String>(new String[0]));  
		
		for (UnorderedRow<String> t : ordered) System.out.println(t);
	}

}
