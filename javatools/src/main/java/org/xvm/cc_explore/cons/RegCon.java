package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class RegCon extends Const {
  private final int _reg;
  public RegCon( FilePart X ) { _reg = (int)X.pack64(); }
}
