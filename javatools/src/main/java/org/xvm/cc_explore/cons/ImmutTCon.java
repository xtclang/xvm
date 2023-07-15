package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.*;

/**
  Exploring XEC Constants
 */
public class ImmutTCon extends TCon {
  TCon _con;
  public ImmutTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) { sb.p("R/O");  return _con==null ? sb : _con.str(sb.p(" -> "));  }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  public TCon icon() { return _con; }
  @Override public TVar _setype(XEC.ModRepo repo) {
    TVar tv = _con.setype(repo);
    // Add the "Immut" interface
    TVStruct tv2 = (TVStruct)tv.fresh();
    tv2._names.add("Immut");
    return tv2;
  }
}
