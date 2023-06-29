package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public class InnerDepTCon extends DepTCon {
  private ClassCon _child;
  private ClassPart _kclz;
  public InnerDepTCon( CPool X ) {
    super(X);
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    _child = (ClassCon)X.xget();
  }
  //@Override public ClassPart link(XEC.ModRepo repo) {
  //  if( _kclz != null ) return _kclz;
  //  super.link(repo);
  //  return (_kclz = (ClassPart)_child.link(repo));
  //}
}