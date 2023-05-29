package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;

/**
  Exploring XEC Constants
 */
public class CharCon extends TCon {
  public final int _ch;
  public CharCon( CPool X ) { _ch = X.utf8Char(); }
}
