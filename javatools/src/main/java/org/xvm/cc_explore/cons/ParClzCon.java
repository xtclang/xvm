package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class ParClzCon extends PartCon {
  private IdCon _child;
  public ParClzCon( CPool X ) { X.u31();  }
  @Override public void resolve( CPool X ) { _child = (IdCon)X.xget(); }
  @Override public String name() { return _child.name(); }
  @Override public IdCon link_as(XEC.ModRepo repo) { throw XEC.TODO(); }
}
