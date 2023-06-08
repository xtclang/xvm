package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class UInt8AryCon extends Const {
  private final byte[] _bs;
  public UInt8AryCon( FilePart X ) { _bs = X.bytes(); }
}
