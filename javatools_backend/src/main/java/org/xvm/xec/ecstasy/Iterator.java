package org.xvm.xec.ecstasy;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xrun.XRuntime;

/**
   XTC Iterator interface and also the Java Iterator interface
*/
public abstract class Iterator<E> extends XTC implements java.util.Iterator<E>  {
  public long next8() { throw XEC.TODO(); }
  public char next2() { throw XEC.TODO(); }
  public String nextStr() { throw XEC.TODO(); }

  // Conditional return known size
  public long knownSize() { return XRuntime.False(0); }
  public boolean knownEmpty() {
    long sz = knownSize();
    return XRuntime.$COND && sz==0;
  }

  Iterator<E> concat(Iterator<E> that) { throw XEC.TODO(); }
}
