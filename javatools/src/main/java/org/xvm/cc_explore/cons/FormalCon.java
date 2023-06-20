package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants.
 */
public abstract class FormalCon extends NamedCon {
  FormalCon( CPool X ) { super(X); }
  // This guy does not have a matching Part/Component/Structure
  @Override public Part link( XEC.ModRepo repo ) {
    _par.link(repo).link(repo);
    return null;
  }
}
