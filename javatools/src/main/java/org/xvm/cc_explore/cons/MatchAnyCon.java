package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MatchAnyCon extends Const {
  final Format _f;
  private Const _con;
  public MatchAnyCon( CPool X, Const.Format f ) {
    _f = f;
    X.u31();
  }  
  @Override public void resolve( CPool X ) { _con = X.xget(); }
}
