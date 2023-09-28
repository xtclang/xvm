package org.xvm.cc_explore.xrun;
import org.xvm.cc_explore.XEC;

public class TupleN extends Tuple {
  final Object[] _es;
  final int len() { return _es.length; }
  final Object get(int i) { return _es[i]; }
  TupleN(Object[] es) { _es = es; }  
}

