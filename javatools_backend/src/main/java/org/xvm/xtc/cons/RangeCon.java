package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.IntCon;

/**
  Exploring XEC Constants
 */
public class RangeCon extends Const {
  private final Format _f;
  public final boolean _xlo, _xhi; // Exclude lo, hi
  public Const _lo, _hi;
  private Part _plo;            // Optional; some IntCons have no part
  public RangeCon( CPool X, Const.Format f ) {
    _f = f;
    int b = switch( f ) {
    case Range -> X.u8();
    case RangeExclusive -> 2;
    case RangeInclusive -> 0;
    default -> throw new IllegalArgumentException("illegal format: "+f);
    };
    _xlo = (b&1)!=0;
    _xhi = (b&2)!=0;
    X.u31();
    X.u31();
  }
  public long lo() { return ((NumCon)_lo)._x; }
  public long hi() { return ((NumCon)_hi)._x; }
  @Override public void resolve( CPool X ) {
    if( _f == Format.Range ) X.u8();
    _lo = X.xget();
    _hi = X.xget();
  }
}
