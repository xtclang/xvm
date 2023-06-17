package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class AccessTCon extends TCon {
  private final Access _access;
  TCon _con;
  ClassPart _clz;
  public AccessTCon( CPool X ) {
    X.u31();                    // Skip index for _con
    _access = Access.valueOf(X.u31());
  }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  public Part link(XEC.ModRepo repo) {
    return _clz==null ? (_clz=(ClassPart)_con.link(repo)) : _clz;
  }
}
