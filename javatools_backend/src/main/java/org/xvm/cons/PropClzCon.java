package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class PropClzCon extends DepTCon {
  private PropCon _prop;
  public PropClzCon( CPool X ) { super(X); X.u31(); }
  @Override public void resolve( CPool X ) { super.resolve(X); _prop = (PropCon)X.xget(); }  
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    ClassPart par = (ClassPart)_par.link(repo);
    return (_part = par.child(_prop._name));
  }
}
