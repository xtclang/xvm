package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;

/**
  Exploring XEC Constants
 */
public class ByteCon extends NumCon {
  final Format _f;
  public ByteCon( CPool X, Format f ) {
    super(isSigned(f) ? X.i8() : X.u8());
    _f = f;
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
