package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class SingleCon extends Const {
  final Format _f;
  private final transient int _x; // Index for actual const
  IdCon _clz;
  public SingleCon( FilePart X, Format f ) {
    _f = f;
    _x = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _clz = (IdCon)pool.get(_x);
  }
}
