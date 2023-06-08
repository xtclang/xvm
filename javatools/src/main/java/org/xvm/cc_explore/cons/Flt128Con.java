package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class Flt128Con extends Const {
  private final byte[] _buf;   
  public Flt128Con( FilePart X ) { _buf = X.bytes(16); }
  @Override public void resolve( CPool pool ) { }
}
