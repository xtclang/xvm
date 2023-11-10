package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.ClassPart;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

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
  @Override public SB str(SB sb) { return _con.str(sb.p(_access.toString()).p(" -> ")); }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  @Override public Part link( XEC.ModRepo repo ) {
    return _clz==null ? ( _clz=(ClassPart)_con.link(repo) ) : _clz;
  }
}
