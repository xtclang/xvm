package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class RangeCon extends Const {
  private final transient int _lox, _hix; 
  private final boolean _xlo, _xhi; // Exclude lo, hi
  private Const _lo, _hi;
  public RangeCon( FilePart X, Const.Format f ) {
    int b = switch( f ) {
    case Range -> X.u8();
    case RangeExclusive -> 2;
    case RangeInclusive -> 0;
    default -> throw new IllegalArgumentException("illegal format: "+f);
    };
    _xlo = (b&1)!=0;
    _xhi = (b&2)!=0;
    _lox = X.u31();
    _hix = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _lo = pool.get(_lox);
    _hi = pool.get(_hix);
  }
  @Override public Const resolveTypedefs() { throw XEC.TODO(); }
}
