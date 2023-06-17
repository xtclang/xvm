package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class ThisClzCon extends PartCon {
  private IdCon _clz;
  public ThisClzCon( CPool X ) { X.u31();  }
  @Override public void resolve( CPool X ) { _clz = (ClassCon)X.xget(); }
  @Override public String name() { return _clz.name(); }
}
