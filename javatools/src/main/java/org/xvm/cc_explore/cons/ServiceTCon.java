package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public class ServiceTCon extends TCon {
  private TCon _con;
  private ClassPart _clz;
  public ServiceTCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  //@Override public ClassPart link(XEC.ModRepo repo) {
  //  return _clz==null ? (_clz=(ClassPart)_con.link(repo)) : _clz;
  //}
}
