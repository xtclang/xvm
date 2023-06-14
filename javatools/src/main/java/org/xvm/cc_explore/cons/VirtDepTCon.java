package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class VirtDepTCon extends DepTCon {
  private final boolean _thisClz;
  private StringCon _name;
  public VirtDepTCon( FilePart X ) {
    super(X);
    X.u31();
    _thisClz = X.u1();
  }
  @Override public void resolve( FilePart X ) {
    super.resolve(X);
    _name = (StringCon)X.xget();
  }
}
