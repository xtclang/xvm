package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class Flt128Con extends Const {
  private final byte[] _buf;   
  public Flt128Con( CPool X ) { _buf = X.bytes(16); }
}
