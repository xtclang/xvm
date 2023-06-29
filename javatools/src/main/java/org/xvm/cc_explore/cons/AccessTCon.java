package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public class AccessTCon extends TCon {
  private final Access _access;
  TCon _con;
  Part _type;
  public AccessTCon( CPool X ) {
    X.u31();                    // Skip index for _con
    _access = Access.valueOf(X.u31());
  }
  @Override public SB str(SB sb) { return _con.str(sb.p(_access.toString()).p(" -> ")); }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  //public Part link(XEC.ModRepo repo) {
  //  return _type==null ? (_type=_con.link(repo)) : _type;
  //}
}
