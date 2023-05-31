package org.xvm.cc_explore;

import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class ParamTConst extends TConst {
  private transient int _tx;  // Type index for type
  private transient int[] _txs;  // Type index for type parameters
  
  ParamTConst( XEC.XParser X ) throws IOException {
    _tx = X.index();
    _txs = X.idxAry();
  }
  @Override void resolve( CPool pool ) {
    throw XEC.TODO();
  }
}
