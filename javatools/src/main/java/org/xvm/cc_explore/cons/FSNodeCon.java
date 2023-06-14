package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class FSNodeCon extends TCon {
  private final Format _f;
  private StringCon _name;
  private LitCon _create, _mod;
  private Const _data;
  public FSNodeCon( FilePart X, Format f ) {
    _f = f;
    X.u31();
    X.u31();
    X.u31();
    X.u31();
  }
  @Override public void resolve( FilePart X ) {
    _name   = (StringCon)X.xget();
    _create = (   LitCon)X.xget();
    _mod    = (   LitCon)X.xget();
    _data   =            X.xget();
  }
}
