package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class Flt8e4Con extends Const {
  private final float _flt;
  public Flt8e4Con( FilePart X ) {
    _flt = switch( X.u8() ) {
    case 0x00 ->  0.0f;
    case 0x80 -> -0.0f;
    case 0x7F -> Float.NaN;
    case 0xFF -> Float.intBitsToFloat(0xFFC00000);
    default -> throw new UnsupportedOperationException("TODO implement non-zero E4M3 float values");
    };
  }
  @Override public void resolve( CPool pool ) {} 
}
