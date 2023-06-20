package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  General Con class backed by matching Part class
 */
public abstract class PartCon extends IdCon {
  PartCon _par;                 // Parent
  Part _part;
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    // Link the parent, do any replacement lookups
    //Part par = _par.link(repo).link(repo);
    Part par = _par.link(repo);
    if( par==null ) {
      System.err.println("Cannot find "+name()+" in "+_par.name());
      return null;
    }
    par = par.link(repo);
    // Find the child in the parent
    return (_part = par.child(name(),repo));
  }
  @Override Part part() { assert _part!=null; return _part; }
}
