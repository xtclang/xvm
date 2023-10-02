package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public abstract class DepTCon extends TCon implements ClzCon {
  TCon _par;
  Part _part;
  DepTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) {
    sb.p("<dep>");
    return _par==null ? sb : _par.str(sb.p(" -> "));
  }
  @Override public ClassPart clz() { return (ClassPart)_part; }
  @Override public void resolve( CPool X ) { _par = (TCon)X.xget(); }
  abstract public Part link(XEC.ModRepo repo);
}
