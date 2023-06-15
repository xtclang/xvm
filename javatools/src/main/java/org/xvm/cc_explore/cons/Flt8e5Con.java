package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class Flt8e5Con extends Const {
  private final float _flt;
  public Flt8e5Con( CPool X ) {
    _flt = switch( X.u8() ) {
    case 0x00 ->  0.0f;
    case 0x80 -> -0.0f;
    case 0x7F -> Float.NaN;
    case 0xFF -> Float.intBitsToFloat(0xFFC00000);
    case 0x7C -> Float.POSITIVE_INFINITY;
    case 0xFC -> Float.NEGATIVE_INFINITY;
    default -> throw new UnsupportedOperationException("TODO implement non-zero E5M2 float values");
    };
  }
}
