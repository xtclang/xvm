package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class Flt64Con extends Const {
  public final double _flt;
  public Flt64Con( CPool X ) {
    _flt = Double.longBitsToDouble(X.i64());
  }
}
