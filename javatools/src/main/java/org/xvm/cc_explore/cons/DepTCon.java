package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public abstract class DepTCon extends TCon {
  private TCon _par;
  private Part _clz;
  DepTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) {
    sb.p("<dep>");
    return _par==null ? sb : _par.str(sb.p(" -> "));
  }
  @Override public void resolve( CPool X ) { _par = (TCon)X.xget(); }
  @Override public Part link(XEC.ModRepo repo) {
    return _clz==null ? (_clz = _par.link(repo)) : _clz;
  }
}
