package org.xvm.cons;

import org.xvm.CPool;
import org.xvm.CPool;

/**
  Exploring XEC Constants
 */
public class RegCon extends Const {
  private final int _reg;
  public RegCon( CPool X ) { _reg = (int)X.pack64(); }
}
