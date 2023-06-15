package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class VirtDepTCon extends DepTCon {
  private final boolean _thisClz;
  private String _name;
  public VirtDepTCon( CPool X ) {
    super(X);
    X.u31();
    _thisClz = X.u1();
  }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    _name =((StringCon)X.xget())._str;
  }
}
