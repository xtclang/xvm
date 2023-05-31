package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class ParamTCon extends TCon {
  private transient int _tx;  // Type index for type
  private transient int[] _txs;  // Type index for type parameters
  
  public ParamTCon( XEC.XParser X ) throws IOException {
    _tx = X.index();
    _txs = X.idxAry();
  }
  @Override public void resolve( CPool pool ) {
    throw XEC.TODO();
  }
}
