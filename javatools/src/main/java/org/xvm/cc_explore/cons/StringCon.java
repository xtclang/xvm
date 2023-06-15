package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;

/**
  Exploring XEC Constants
 */
public class StringCon extends Const {
  public final String _str;
  public StringCon( CPool X ) { _str = X.utf8(); }
  @Override public String toString() { return _str; }
}
