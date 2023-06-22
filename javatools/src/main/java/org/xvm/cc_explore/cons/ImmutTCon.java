package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class ImmutTCon extends TCon {
  TCon _con;
  private XType _type;       // An immutable view over a Class or Property
  public ImmutTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) { sb.p("R/O");  return _con==null ? sb : _con.str(sb.p(" -> "));  }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  @Override public XType link(XEC.ModRepo repo) {
    return _type==null ? (_type=_con.link(repo)) : _type;
  }
}
