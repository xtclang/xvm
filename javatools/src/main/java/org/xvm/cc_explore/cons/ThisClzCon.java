package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class ThisClzCon extends PsuedoCon {
  private IdCon _clz;
  public ThisClzCon( FilePart X ) { X.u31();  }
  @Override public void resolve( FilePart X ) { _clz = (IdCon)X.xget(); }
}
