package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class SigCon extends IdCon {
  String _name;
  TCon[] _parms;
  TCon[] _rets;
  
  public SigCon( CPool X ) {
    X.u31();
    X.skipAry();
    X.skipAry();
  }
  
  @Override public void resolve( CPool X ) {
    _name  =((StringCon)X.xget())._str;
    _parms = TCon.tcons(X);
    _rets  = TCon.tcons(X);
  }  
  @Override public String name() { return _name; }
  public TCon[] rawRets () { return _rets ; }
  public TCon[] rawParms() { return _parms; }
  @Override public Part link(XEC.ModRepo repo) { throw XEC.TODO(); }
}
