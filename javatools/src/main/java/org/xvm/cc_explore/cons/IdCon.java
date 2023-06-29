package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
  A forwarding pointer to another component
 */
public abstract class IdCon extends Const {
  abstract public String name();

  // Convert e.g. ClassCon/ModCon/PackCon to their Part equivalents.
  public Part link(XEC.ModRepo repo) { return null; }
  
  TVar _tvar;
  public final TVar tvar() {
    assert _tvar!=null;
    return _tvar.unified() ? (_tvar=_tvar.find()) : _tvar;
  }
}
