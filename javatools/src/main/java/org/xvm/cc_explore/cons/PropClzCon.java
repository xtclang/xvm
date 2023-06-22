package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class PropClzCon extends DepTCon {
  private PropCon _prop;
  private PropPart _part;
  public PropClzCon( CPool X ) { super(X); X.u31(); }
  @Override public void resolve( CPool X ) { super.resolve(X); _prop = (PropCon)X.xget(); }  
  @Override public PropPart link(XEC.ModRepo repo) {
    if( _part!=null ) return _part;
    ClassPart p = (ClassPart)super.link(repo);
    return (_part = (PropPart)p._name2kid.get(_prop._name));
  }
}
