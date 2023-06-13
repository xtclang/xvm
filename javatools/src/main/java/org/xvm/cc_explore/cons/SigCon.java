package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class SigCon extends IdCon {
  private transient int _namex;  // Type index for name
  private transient int[] _parmxs, _retxs;  // Type index arrays for parms, returns
  StringCon _name;
  TCon[] _parms;
  TCon[] _rets;
  
  public SigCon( FilePart X ) {
    _namex  = X.u31();
    _parmxs = X.idxAry();
    _retxs  = X.idxAry();
  }
  
  @Override public void resolve( CPool pool ) {
    _name  = (StringCon)pool.get(_namex);
    _parms = TCon.resolveAry(pool,_parmxs);
    _rets  = TCon.resolveAry(pool, _retxs);
  }  
  @Override public String name() { throw XEC.TODO(); }
  public TCon[] rawRets () { return _rets ; }
  public TCon[] rawParms() { return _parms; }
}
