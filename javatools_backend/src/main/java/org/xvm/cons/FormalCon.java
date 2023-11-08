package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants.
 */
public abstract class FormalCon extends NamedCon {
  FormalCon( CPool X ) { super(X); }
  @Override public Part link( XEC.ModRepo repo ) { return null; }  
}
