package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class Flt64Con extends Const {
  public final double _flt;
  public Flt64Con( FilePart X ) {
    _flt = Double.longBitsToDouble(X.i64());
  }
}
