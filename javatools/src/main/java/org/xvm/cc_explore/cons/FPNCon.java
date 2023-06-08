package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class FPNCon extends Const {
  final Format _f;
  private final byte[] _buf;
  public FPNCon( FilePart X, Format f ) {
    _f = f;
    int len = (1 << X.u8());
    _buf = X.bytes(len);
    int min = f==Format.DecN ? 4 : 2;
    if( len < min || len > 16384 )
      throw new IllegalArgumentException("value length ("+len+") must be a power-of-two between " + min + " and 16384");
  }
}
