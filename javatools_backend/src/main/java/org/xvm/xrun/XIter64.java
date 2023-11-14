package org.xvm.xrun;

import org.xvm.xtc.cons.RangeCon;
import org.xvm.xec.XClz;

import java.lang.Iterable;
import java.util.Iterator;

/**
     Support XTC range iterator
*/
public class XIter64 extends XClz implements Iterator<Long> {
  long _i;
  final long _end;
  final boolean _dn;
  
  XIter64( long i, long end ) {
    _i = i;
    _end = end;
    _dn = i > end;
  }

  @Override public final String toString() { return ""+_i+".."+_end; }

  @Override public boolean hasNext() { return _i!=_end; }
  @Override public Long next() { return XRuntime.SET$COND(hasNext(), _dn ? --_i : _i++); }
}