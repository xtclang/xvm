package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;

/**
  Exploring XEC Constants
 */
public class RegCon extends Const {
  private final int _reg;
  public RegCon( CPool X ) { _reg = (int)X.pack64(); }
}
