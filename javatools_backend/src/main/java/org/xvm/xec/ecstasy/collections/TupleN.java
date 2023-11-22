package org.xvm.xec.ecstasy.collections;

public class TupleN extends Tuple {
  static final int KID = GET_KID(new TupleN());
  public int kid() { return KID; }
  TupleN() { super(0); _es=null; } // No arg constructor
  
  private final Object[] _es;
  public final Object at(int i) { return _es[i]; }
  public final void set(int i, Object e) { _es[i]=e; }
  TupleN(Object[] es) { super(es.length); _es = es; }  
}
