package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.cons.RangeCon;
import org.xvm.cc_explore.xclz.XClz;

import java.lang.Iterable;
import java.util.Iterator;

/**
     Support XTC range iterator
*/
abstract public class Range extends XClz implements Iterable<Long> {
  final long _lo, _hi;          // Inclusive lo, exclusive hi
  final boolean _lx, _hx;       // True if exclusive
  final boolean _invert;        // Inverted range
  Range( long lo, long hi, boolean lx, boolean hx ) {
    boolean invert=false;
    if( lo > hi ) {
      long tmp=lo; lo=hi; hi=tmp;
      boolean b=lx; lx=hx; hx=b;
      invert=true;
    }
    if( !hx ) hi++;
    if(  lx ) lo++;
    _lo=lo;
    _hi=hi;
    _lx=lx;
    _hx=hx;
    _invert = invert;
  }

  @Override
  public final String toString() {
    return
      (_lx ? "("+(_lo-1) : "["+_lo ) +
      ".." +
      (!_hx ? ""+(_hi-1)+"]" : ""+_hi+")" );
  }

  public long span() { return _hi-_lo; }
  public static long lo(RangeCon rcon) { return rcon.lo() + (rcon._xlo ? 1 : 0); }
  public static long hi(RangeCon rcon) { return rcon.hi() + (rcon._xhi ? 0 : 1); }
  public boolean in( long x ) { return _lo <= x && x < _hi; }
  
  /** @return an iterator */
  @Override public XIter64 iterator() { return _invert ? new XIter64(_hi,_lo) : new XIter64(_lo,_hi); }
}
