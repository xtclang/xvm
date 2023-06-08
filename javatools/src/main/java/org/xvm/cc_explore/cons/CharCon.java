package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.math.BigInteger;

/**
  Exploring XEC Constants
 */
public class CharCon extends Const {
  private final int _ch;
  public CharCon( FilePart X ) { _ch = X.utf8Char(); }
}
