package org.xvm.xec.ecstasy;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.numbers.Int64;
import org.xvm.xtc.XClz;
import org.xvm.xtc.ast.AST;
import org.xvm.xtc.ast.ConAST;
import org.xvm.xtc.XCons;
import org.xvm.xtc.cons.RangeCon;

import java.lang.Iterable;
import java.util.Iterator;

/**
     Support XTC range iterator
*/
abstract public class AbstractRange extends XTC implements Iterable<Int64> {
  public AbstractRange( ) {_start=_end=0; _lx=_hx=false; _incr=1; } // No arg constructor

  public final long _start, _end, _incr; // Inclusive start, exclusive end
  public final boolean _lx, _hx;       // True if exclusive
  AbstractRange( long start, long end, boolean lx, boolean hx ) {
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

  @Override public final String toString() {
    return
      (_lx ? "("+(_start-_incr) : "["+_start ) +
      ".." +
      (!_hx ? ""+(_end-_incr)+"]" : ""+_end+")" );
  }

  public long span() { return _end-_start; }
  public static long start(RangeCon rcon) { return rcon.lo() + (rcon._xlo ? 1 : 0); }
  public static long end  (RangeCon rcon) { return rcon.hi() + (rcon._xhi ? 0 : 1); }
  public boolean in( long x ) { return _incr == 1 ? x < _end : x > _end; }

  public static AbstractRange range(AST ast) {
    XClz rng = (XClz)ast.type();
    int lo = con(ast._kids[0]);
    int hi = con(ast._kids[1]);
    if( rng==XCons.RANGEII ) return new RangeII(lo,hi);
    throw XEC.TODO();
  }
  // Peel off a "L" from a ConAST of "3L"
  private static int con(AST ast) {
    String s = (((ConAST)ast))._con;
    return Integer.valueOf(s.substring(0,s.length()-1));
  }


  /** @return an iterator */
  @Override public Iterator iterator() {
    throw XEC.TODO();
  }
  // --- Comparable
  public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }

}
