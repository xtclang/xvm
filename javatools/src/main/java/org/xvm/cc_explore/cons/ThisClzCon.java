package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class ThisClzCon extends PsuedoCon {
  private final transient int _clzx;
  private IdCon _clz;
  public ThisClzCon( FilePart X ) { _clzx = X.u31();  }
  @Override public void resolve( CPool pool ) { _clz = (IdCon)pool.get(_clzx); }
}
