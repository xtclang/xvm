package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class LitCon extends Const {
  final Format _f;
  String _str;                  // The actual string constant
  public LitCon( CPool X, Format f ) {
    _f = f;
    X.u31();
  }
  @Override public SB str(SB sb) { return sb.p(_str); }
  @Override public void resolve( CPool X ) { _str = ((StringCon)X.xget())._str; }
}
