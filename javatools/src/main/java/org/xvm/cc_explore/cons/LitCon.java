package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class LitCon extends Const {
  final Format _f;
  StringCon _str;               // The actual string constant
  public LitCon( FilePart X, Format f ) {
    _f = f;
    X.u31();
  }
  @Override public void resolve( FilePart X ) { _str = (StringCon)X.xget(); }
}
