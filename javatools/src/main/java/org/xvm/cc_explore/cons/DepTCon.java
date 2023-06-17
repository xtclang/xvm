package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public abstract class DepTCon extends TCon {
  private TCon _par;
  private ClassPart _clz;
  DepTCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _par = (TCon)X.xget(); }
  @Override public Part link(XEC.ModRepo repo) {
    return _clz==null ? (_clz = (ClassPart)_par.link(repo)) : _clz;
  }
}
