package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class ImmutTCon extends TCon {
  TCon _con;
  private ClassPart _clz;       // An immutable view over a Class
  public ImmutTCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  @Override public Part link(XEC.ModRepo repo) {
    if( _clz!=null ) return _clz;
    return (_clz = (ClassPart)_con.link(repo));
  }
}
