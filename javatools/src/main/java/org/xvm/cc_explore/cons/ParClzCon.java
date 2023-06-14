package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class ParClzCon extends PsuedoCon {
  private PsuedoCon _child;
  public ParClzCon( FilePart X ) { X.u31();  }
  @Override public void resolve( FilePart X ) { _child = (PsuedoCon)X.xget(); }
}
