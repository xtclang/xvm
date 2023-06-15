package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon {
  private IdCon _id;
  public TermTCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _id = (IdCon)X.xget(); }
  public IdCon id() { return _id; }
}
