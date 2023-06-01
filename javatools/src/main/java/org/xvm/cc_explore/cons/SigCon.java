package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class SigCon extends IdCon {
  private transient int _namex;  // Type index for name
  private transient int[] _parmxs, _retxs;  // Type index arrays for parms, returns
  StringCon _name;
  TCon[] _params;
  TCon[] _rets;
  
  public SigCon( FileComponent X ) throws IOException {
    _namex  = X.u31();
    _parmxs = X.idxAry();
    _retxs  = X.idxAry();
  }
  
  @Override public void resolve( CPool pool ) {
    _name = (StringCon)pool.get(_namex);
    _params = new TCon[_parmxs.length];
    _rets   = new TCon[_retxs .length];
    for( int i=0; i<_parmxs.length; i++ )  _params[i] = (TCon)pool.get(_parmxs[i]);
    for( int i=0; i<_retxs .length; i++ )  _rets  [i] = (TCon)pool.get(_retxs [i]);
  }  
  @Override public Const resolveTypedefs() { throw XEC.TODO(); }
}
