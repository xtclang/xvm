package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;

/**
  Exploring XEC Constants
 */
public class CharCon extends NumCon {
  public CharCon( CPool X ) { super(X.utf8Char()); }
}
