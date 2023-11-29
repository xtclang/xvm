package org.xvm.xec.ecstasy;

import org.xvm.xec.XTC;
import org.xvm.xrun.Never;
import org.xvm.xrun.XRuntime;

import java.util.Iterator;

/**
     Support XTC range iterator
*/
public class Iterablelong extends XTC implements Iterator<Long> {
  public static final Iterablelong GOLD = new Iterablelong(null);
  public Iterablelong(Never n ) { _end=0; _dn=false; } // No-arg constructor
  
  long _i;
  final long _end;
  final boolean _dn;
  
  public Iterablelong( long i, long end ) {
    _i = i;
    _end = end;
    _dn = i > end;
  }

  @Override public final String toString() { return ""+_i+".."+_end; }

  @Override public boolean hasNext() { return _i!=_end; }
  @Override public Long next() { return XRuntime.SET$COND(hasNext(), _dn ? --_i : _i++); }
}
