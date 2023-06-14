package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class DecClzCon extends IdCon {
  private TCon _type;
  public DecClzCon( FilePart X ) { X.u31(); }
  @Override public void resolve( FilePart X ) { _type = (TCon)X.xget(); }
  @Override public String name() { throw XEC.TODO(); }
}
