package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class PropCon extends FormalCon {
  public PropCon( CPool X ) { super(X); }
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    // Link the parent, do any replacement lookups
    Part par = _par.link(repo).link(repo);
    // Just the parent, I guess
    return (_part = par);
  }
}
