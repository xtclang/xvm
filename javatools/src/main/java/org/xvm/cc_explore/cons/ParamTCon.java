package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class ParamTCon extends TCon {
  private transient int _tx;  // Type index for type
  private transient int[] _txs;  // Type index for type parameters
  TCon _con;
  TCon[] _params;
  
  public ParamTCon( FileComponent X ) throws IOException {
    _tx  = X.u31();
    _txs = X.idxAry();
  }
  @Override public void resolve( CPool pool ) {
    _con = (TCon)pool.get(_tx);
    _params = new TCon[_txs.length];
    for( int i=0; i<_txs.length; i++ )  _params[i] = (TCon)pool.get(_txs[i]);
  }
  @Override public Const resolveTypedefs() { throw XEC.TODO(); }
}
