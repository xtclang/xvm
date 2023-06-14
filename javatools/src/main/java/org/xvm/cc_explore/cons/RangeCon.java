package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class RangeCon extends Const {
  private final Format _f;
  private final boolean _xlo, _xhi; // Exclude lo, hi
  private Const _lo, _hi;
  public RangeCon( FilePart X, Const.Format f ) {
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
  @Override public void resolve( FilePart X ) {
    if( _f == Format.Range ) X.u8();
    _lo = X.xget();
    _hi = X.xget();
  }
}
