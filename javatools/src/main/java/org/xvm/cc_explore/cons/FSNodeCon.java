package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class FSNodeCon extends TCon {
  private final Format _f;
  private final transient int _namex,_createx,_modx,_datax;
  private StringCon _name;
  private LitCon _create, _mod;
  private Const _data;
  public FSNodeCon( FilePart X, Format f ) {
    _f = f;
    _namex  = X.u31();
    _createx= X.u31();
    _modx   = X.u31();
    _datax  = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _name   = (StringCon)pool.get(  _namex);
    _create = (   LitCon)pool.get(_createx);
    _mod    = (   LitCon)pool.get(   _modx);
    _data   =            pool.get(  _datax);
  }
}
