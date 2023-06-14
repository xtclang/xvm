package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class SingleCon extends Const {
  final Format _f;
  IdCon _clz;
  public SingleCon( FilePart X, Format f ) {
    _f = f;
    X.u31();
  }
  @Override public void resolve( FilePart X ) { _clz = (IdCon)X.xget(); }
}
