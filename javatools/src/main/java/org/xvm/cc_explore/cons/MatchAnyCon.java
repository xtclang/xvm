package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MatchAnyCon extends Const {
  final Format _f;
  private transient int _tx;    // Type index for later
  private Const _con;
  public MatchAnyCon( FilePart X, Const.Format f ) {
    _f = f;
    _tx = X.u31();
  }  
  @Override public void resolve( CPool pool ) { _con = pool.get(_tx); }
  @Override public Const resolveTypedefs() { throw XEC.TODO(); }
}
