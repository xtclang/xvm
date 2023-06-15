package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.CPool;

/**
  Exploring XEC Constants
 */
public class SingleCon extends Const {
  final Format _f;
  IdCon _clz;
  public SingleCon( CPool X, Format f ) {
    _f = f;
    X.u31();
  }
  @Override public void resolve( CPool X ) { _clz = (IdCon)X.xget(); }
}
