package org.xvm.cons;

import org.xvm.*;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class LitCon extends TCon {
  public final Format _f;
  public String _str;           // The actual string constant
  public LitCon( CPool X, Format f ) {
    _f = f;
    X.u31();
  }
  @Override public SB str(SB sb) { return sb.p(_str); }
  @Override public void resolve( CPool X ) { _str = ((StringCon)X.xget())._str; }
}
