package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class SigCon extends IdCon {
  StringCon _name;
  TCon[] _parms;
  TCon[] _rets;
  
  public SigCon( FilePart X ) {
    X.u31();
    X.skipAry();
    X.skipAry();
  }
  
  @Override public void resolve( FilePart X ) {
    _name  = (StringCon)X.xget();
    _parms = TCon.tcons(X);
    _rets  = TCon.tcons(X);
  }  
  @Override public String name() { throw XEC.TODO(); }
  public TCon[] rawRets () { return _rets ; }
  public TCon[] rawParms() { return _parms; }
}
