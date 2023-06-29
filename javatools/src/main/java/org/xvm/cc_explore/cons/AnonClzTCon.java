package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public class AnonClzTCon extends DepTCon {
  private ClassCon _anon;
  private ClassPart _aclz;
  public AnonClzTCon( CPool X ) { super(X); X.u31(); }
  @Override public void resolve( CPool X ) { super.resolve(X); _anon = (ClassCon)X.xget(); }
  //@Override public ClassPart link(XEC.ModRepo repo) {
  //  if( _aclz!=null ) return _aclz;
  //  super.link(repo);
  //  return (_aclz = (ClassPart)_anon.link(repo));
  //}
}
