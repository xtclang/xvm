package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class Flt64Con extends Const {
  public final double _flt;
  public Flt64Con( CPool X ) {
    _flt = Double.longBitsToDouble(X.i64());
  }
}
