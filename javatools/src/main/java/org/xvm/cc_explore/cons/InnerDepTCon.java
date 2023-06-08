package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class InnerDepTCon extends DepTCon {
  private final transient int _cx;
  private ClassCon _child;
  public InnerDepTCon( FilePart X ) {
    super(X);
    _cx = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    super.resolve(pool);
    _child = (ClassCon)pool.get(_cx);
  }
}
