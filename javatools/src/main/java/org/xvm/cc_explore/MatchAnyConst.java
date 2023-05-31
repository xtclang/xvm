package org.xvm.cc_explore;

import java.io.IOException;

/**
  Exploring XEC Constants
 */
public class MatchAnyConst extends Const {
  final Format _f;
  private transient int _tx;    // Type index for later
  private Const _con;
  MatchAnyConst( XEC.XParser X, Const.Format f ) throws IOException {
    _f = f;
    _tx = X.index();
  }  
  @Override void resolve( CPool pool ) {
    _con = pool.get(_tx);
  }
}
