package org.xvm.xec.ecstasy;

import org.xvm.xec.XTC;
import org.xvm.XEC;

/**
   XTC Iterator interface and also the Java Iterator interface
*/
public abstract class Iterator<E> extends XTC implements java.util.Iterator<E>  {
  public long next8() { throw XEC.TODO(); }
  //public char next2() { throw XEC.TODO(); }
  public String nextStr() { throw XEC.TODO(); }
}
