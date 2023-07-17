package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants.
 */
public abstract class FormalCon extends NamedCon {
  FormalCon( CPool X ) { super(X); }
  @Override public Part link( XEC.ModRepo repo ) { return null; }  
}
