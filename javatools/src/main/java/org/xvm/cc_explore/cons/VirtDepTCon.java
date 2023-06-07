package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class VirtDepTCon extends DepTCon {
  private final transient int _namex;
  private final boolean _thisClz;
  private StringCon _name;
  public VirtDepTCon( FilePart X ) {
    super(X);
    _namex = X.u31();
    _thisClz = X.u1();
  }
  @Override public void resolve( CPool pool ) {
    super.resolve(pool);
    _name = (StringCon)pool.get(_namex);
  }
}
