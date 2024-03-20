package org.xvm.xec.ecstasy.collections;

import org.xvm.xec.XTC;

public class TupleN extends Tuple0 {
  public static final TupleN GOLD = new TupleN();
  TupleN() { super(0); _es=null; } // No arg constructor

  private final XTC[] _es;
  TupleN(XTC[] es) { super(es.length); _es = es; }
  @Override public final XTC at(long i) { return _es[(int)i]; }
  @Override public final void set(long i, XTC e) { _es[(int)i]=e; }
}
