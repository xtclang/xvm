package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;

/**
  Exploring XEC Constants.
 */
public abstract class FormalCon extends NamedCon {
  FormalCon( CPool X ) { super(X); }
  @Override public Part link( XEC.ModRepo repo ) { return null; }  
}
