/*
 * Alex Raybosh, 2009-2015
 */

package appenv.async;

import java.util.concurrent.Future;

public interface Result<T> extends Future<T> {
	Object[] getArgs();
}
