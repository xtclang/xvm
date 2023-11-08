package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class InnerDepTCon extends DepTCon {
  private ClassCon _child;
  public InnerDepTCon( CPool X ) {
    super(X);
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    _child = (ClassCon)X.xget();
  }
  @Override public Part link(XEC.ModRepo repo) {
    if( _part != null ) return _part;
    _par.link(repo);
    return (_part = (ClassPart)_child.link(repo));
  }
}
