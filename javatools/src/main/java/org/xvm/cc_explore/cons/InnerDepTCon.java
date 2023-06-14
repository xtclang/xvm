package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class InnerDepTCon extends DepTCon {
  private ClassCon _child;
  public InnerDepTCon( FilePart X ) {
    super(X);
    X.u31();
  }
  @Override public void resolve( FilePart X ) {
    super.resolve(X);
    _child = (ClassCon)X.xget();
  }
}
