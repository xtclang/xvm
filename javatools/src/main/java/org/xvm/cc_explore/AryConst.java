package org.xvm.cc_explore;

import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class AryConst extends Const {
  final Format _f;
  private transient int _tx;    // Type index for whole array
  private transient int[] _txs; // Type index for each element
  private Const[] _cons;
  
  AryConst( XEC.XParser X, Const.Format f ) throws IOException {
    _f = f;
    _tx = X.index();
    _txs = X.idxAry();
  }
  @Override void resolve( CPool pool ) {
    TConst t = (TConst)pool.get(_tx);
    _cons = new Const[_txs.length];
    for( int i=0; i<_txs.length; i++ )
      _cons[i] = pool.get(_txs[i]);
  }
}
