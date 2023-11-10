package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class FSNodeCon extends TCon {
  private final Format _f;
  private String _name;
  private LitCon _create, _mod;
  private Const _data;
  public FSNodeCon( CPool X, Format f ) {
    _f = f;
    X.u31();
    X.u31();
    X.u31();
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    _name   =((StringCon)X.xget())._str;
    _create = (   LitCon)X.xget();
    _mod    = (   LitCon)X.xget();
    _data   =            X.xget();
  }
}
