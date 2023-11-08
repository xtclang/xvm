package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class PropCon extends FormalCon {
  public PropCon( CPool X ) { super(X); }
  @Override public Part link( XEC.ModRepo repo ) { return partlink(repo); }
}
