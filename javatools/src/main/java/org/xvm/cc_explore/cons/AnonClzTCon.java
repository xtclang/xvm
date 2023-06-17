package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class AnonClzTCon extends DepTCon {
  private ClassCon _anon;
  public AnonClzTCon( CPool X ) { super(X); X.u31(); }
  @Override public void resolve( CPool X ) { super.resolve(X); _anon = (ClassCon)X.xget(); }
  public Part link(XEC.ModRepo repo) { throw XEC.TODO(); }
}
