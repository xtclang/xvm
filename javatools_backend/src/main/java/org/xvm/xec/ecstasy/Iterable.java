package org.xvm.xec.ecstasy;

import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.numbers.Int64;
import org.xvm.xec.ecstasy.collections.Arylong;
import org.xvm.xrun.Never;
import org.xvm.xrun.XRuntime;

/**
   XTC Iterable interface and also the Java Iterable interface
*/
public interface Iterable<E> extends java.lang.Iterable<E> {
  abstract public int size$get();
  abstract public Iterator<E> iterator();
}
