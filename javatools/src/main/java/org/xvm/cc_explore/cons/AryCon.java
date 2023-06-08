package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class AryCon extends Const {
  final Format _f;
  private transient int _tx;    // Type index for whole array
  private transient int[] _txs; // Type index for each element
  private Const[] _cons;
  
  public AryCon( FilePart X, Const.Format f ) {
    _f = f;
    _tx  = X.u31();
    _txs = X.idxAry();
  }
  @Override public void resolve( CPool pool ) {
    TCon t = (TCon)pool.get(_tx);
    _cons = resolveAry(pool,_txs);
  }
  @Override public Const resolveTypedefs() { throw XEC.TODO(); }
}