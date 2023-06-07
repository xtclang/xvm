package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class Flt32Con extends Const {
  public final float _flt;
  public Flt32Con( FilePart X ) {
    _flt = Float.intBitsToFloat(X.i32());
  }
  @Override public void resolve( CPool pool ) {}
}
