package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.cons.RangeCon;

import java.lang.Iterable;
import java.util.Iterator;

/**
     Support XTC range iterator
*/
abstract public class Range implements Iterable<Long> {
  final long _lo, _hi;          // Inclusive lo, exclusive hi
  final boolean _lx, _hx;       // True if exclusive
  Range( long lo, long hi, boolean lx, boolean hx ) { _lo=lo; _hi=hi; _lx=lx; _hx=hx; }

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
  @Override public Iterator<Long> iterator() { return new Iter(); }
  private class Iter implements Iterator<Long> {
    long _i=_lo;
    @Override public boolean hasNext() { return _i<_hi; }
    @Override public Long next() { return _i++; }
  }
  
}

