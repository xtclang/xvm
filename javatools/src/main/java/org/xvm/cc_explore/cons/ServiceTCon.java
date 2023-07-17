package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public class ServiceTCon extends TCon {
  private TCon _con;
  private Part _part;
  public ServiceTCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  @Override public Part link( XEC.ModRepo repo ) {
    return _part==null ? (_part=_con.link(repo)) : _part;
  }
  @Override public TVar _setype( XEC.ModRepo repo ) { return _con.setype(repo); }
}
