package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class InnerDepTCon extends DepTCon {
  private ClassCon _child;
  public InnerDepTCon( CPool X ) {
    super(X);
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    _child = (ClassCon)X.xget();
  }
  @Override public Part link(XEC.ModRepo repo) { throw XEC.TODO(); }
}
