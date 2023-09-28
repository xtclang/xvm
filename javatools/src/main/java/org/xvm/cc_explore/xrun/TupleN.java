package org.xvm.cc_explore.xrun;
import org.xvm.cc_explore.XEC;

public class TupleN extends Tuple {
  private final Object[] _es;
  public final Object at(int i) { return _es[i]; }
  public final void set(int i, Object e) { _es[i]=e; }
  TupleN(Object[] es) { super(es.length); _es = es; }  
}

