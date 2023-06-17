package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MethodBindCon extends Const {
  private MethodCon _method;
  private MethodPart _meth;
  public MethodBindCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _method = (MethodCon)X.xget(); }
  @Override public Part link(XEC.ModRepo repo) {
    return _meth==null ? (_meth=_method.link(repo)) : _meth;
  }
}
