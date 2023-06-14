package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class AryCon extends Const {
  final Format _f;
  private TCon _t;              // Type for whole array
  private Const[] _cons;        // Type for each element
  
  public AryCon( FilePart X, Const.Format f ) {
    _f = f;
    X.u31();                    // Type index for whole array
    X.skipAry();                // Index for each element
  }
  @Override public void resolve( FilePart X ) {
    _t = (TCon)X.xget();
    _cons = xconsts(X);
  }
  @Override public Const resolveTypedefs() { throw XEC.TODO(); }
}
