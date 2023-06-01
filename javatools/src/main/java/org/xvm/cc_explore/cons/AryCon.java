package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class AryCon extends Const {
  final Format _f;
  private transient int _tx;    // Type index for whole array
  private transient int[] _txs; // Type index for each element
  private Const[] _cons;
  
  public AryCon( FileComponent X, Const.Format f ) throws IOException {
    _f = f;
    _tx  = X.u31();
    _txs = X.idxAry();
  }
  @Override public void resolve( CPool pool ) {
    TCon t = (TCon)pool.get(_tx);
    _cons = new Const[_txs.length];
    for( int i=0; i<_txs.length; i++ )
      _cons[i] = pool.get(_txs[i]);
  }
  @Override public Const resolveTypedefs() { throw XEC.TODO(); }

}
