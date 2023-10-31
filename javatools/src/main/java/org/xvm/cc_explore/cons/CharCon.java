package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class CharCon extends TCon {
  public final int _ch;
  public CharCon( CPool X ) { _ch = X.utf8Char(); }
}
