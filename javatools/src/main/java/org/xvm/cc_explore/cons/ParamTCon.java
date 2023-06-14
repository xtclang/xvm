package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class ParamTCon extends TCon {
  TCon _con;
  TCon[] _parms;
  
  public ParamTCon( FilePart X ) {
    X.u31();
    X.skipAry();
  }
  @Override public void resolve( FilePart X ) {
    _con = (TCon)X.xget();
    _parms = TCon.tcons(X);
  }
}
