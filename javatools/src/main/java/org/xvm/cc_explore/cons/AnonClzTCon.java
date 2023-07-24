package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public class AnonClzTCon extends DepTCon {
  private ClassCon _anon;
  public AnonClzTCon( CPool X ) { super(X); X.u31(); }
  @Override public void resolve( CPool X ) { super.resolve(X); _anon = (ClassCon)X.xget(); }
  @Override public Part link(XEC.ModRepo repo) {
    if( _part!=null ) return _part;
    _par.link(repo);
    return (_part = _anon.link(repo));
  }
}
