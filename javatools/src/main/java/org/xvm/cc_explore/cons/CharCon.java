package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVBase;

/**
  Exploring XEC Constants
 */
public class CharCon extends TCon {
  private final int _ch;
  public CharCon( CPool X ) { _ch = X.utf8Char(); }
  @Override TVBase _setype() { return new TVBase(this); }
}
