package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;

/**
  Exploring XEC Constants
 */
public class UInt8AryCon extends Const {
  private final byte[] _bs;
  public UInt8AryCon( CPool X ) { _bs = X.bytes(); }
}
