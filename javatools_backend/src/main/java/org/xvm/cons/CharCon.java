package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class CharCon extends TCon {
  public final int _ch;
  public CharCon( CPool X ) { _ch = X.utf8Char(); }
}
