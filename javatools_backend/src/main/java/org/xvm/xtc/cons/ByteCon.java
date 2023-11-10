package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;

/**
  Exploring XEC Constants
 */
public class ByteCon extends Const {
  final Format _f;
  private final int _val;
  public ByteCon( CPool X, Format f ) {
    _f = f;
    _val = isSigned(f) ? X.i8() : X.u8();
  }

  /**
   * Determine if the specified format is a signed format.
   *
   * @param format  a format supported by this constant class
   *
   * @return true if the format is signed
   */
  static private boolean isSigned(Format format) { return format == Format.Int8; }
}
