package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class StringCon extends Const {
  final String _str;
  public StringCon( FilePart X ) {
    _str = X.utf8();
  }
  @Override public void resolve( CPool pool ) {}
}
