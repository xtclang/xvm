package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class DecClzCon extends IdCon {
  private transient int _tx;
  private TCon _type;
  public DecClzCon( FilePart X ) { _tx = X.u31(); }
  @Override public void resolve( CPool pool ) { _type = (TCon)pool.get(_tx); }
  @Override public String name() { throw XEC.TODO(); }
}
