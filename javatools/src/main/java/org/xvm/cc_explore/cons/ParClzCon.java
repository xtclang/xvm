package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class ParClzCon extends PsuedoCon {
  private final transient int _clzx;
  private PsuedoCon _child;
  public ParClzCon( FilePart X ) { _clzx = X.u31();  }
  @Override public void resolve( CPool pool ) { _child = (PsuedoCon)pool.get(_clzx); }
}
