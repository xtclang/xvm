package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.*;

/**
  Exploring XEC Constants
 */
public class ImmutTCon extends TCon implements ClzCon {
  TCon _con;
  private Part _part;
  public ImmutTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) { sb.p("R/O");  return _con==null ? sb : _con.str(sb.p(" -> "));  }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  public TCon icon() { return _con; }
  @Override public ClassPart clz() { return ((ClzCon)_con).clz(); }
  @Override public Part link(XEC.ModRepo repo) {
    return _part==null ? (_part = _con.link(repo)) : _part;
  }
}
