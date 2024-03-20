package org.xvm.xec.ecstasy.collections;

public class TupleN extends Tuple0 {
  public static final TupleN GOLD = new TupleN();
  TupleN() { super(0); _es=null; } // No arg constructor

  private final Object[] _es;
  TupleN(Object[] es) { super(es.length); _es = es; }
  @Override public final Object at(long i) { return _es[(int)i]; }
  @Override public final void set(long i, Object e) { _es[(int)i]=e; }
}
