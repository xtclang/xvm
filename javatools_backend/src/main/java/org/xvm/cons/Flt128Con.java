package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class Flt128Con extends Const {
  private final byte[] _buf;   
  public Flt128Con( CPool X ) { _buf = X.bytes(16); }
}
