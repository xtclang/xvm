package org.xvm.xec.ecstasy;

import org.xvm.XEC;
import org.xvm.xtc.cons.RangeCon;
import org.xvm.xec.ecstasy.numbers.Int64;
import org.xvm.xec.XTC;

import java.lang.Iterable;
import java.util.Iterator;

/**
     Support XTC range iterator
*/
abstract public class Range extends XTC implements Iterable<Int64> {
  public Range( ) {_start=_end=0; _lx=_hx=false; _incr=1; } // No arg constructor
  
  public final long _start, _end, _incr; // Inclusive start, exclusive end
  public final boolean _lx, _hx;       // True if exclusive
  Range( long start, long end, boolean lx, boolean hx ) {
    int incr = 1;
    if( start > end )
      incr = -1;
    if( !hx ) end  +=incr;
    if(  lx ) start+=incr;
    _start=start;
    _end=end;
    _lx=lx;
    _hx=hx;
    _incr = incr;
  }

  @Override
  public final String toString() {
    return
      (_lx ? "("+(_start-_incr) : "["+_start ) +
      ".." +
      (!_hx ? ""+(_end-_incr)+"]" : ""+_end+")" );
  }

  public long span() { return _end-_start; }
  public static long start(RangeCon rcon) { return rcon.lo() + (rcon._xlo ? 1 : 0); }
  public static long end  (RangeCon rcon) { return rcon.hi() + (rcon._xhi ? 0 : 1); }
  public boolean in( long x ) { return _incr == 1 ? x < _end : x > _end; }
  
  /** @return an iterator */
  @Override public Iterator iterator() {
    throw XEC.TODO();
  }
  // --- Comparable
  public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }  
  
}
