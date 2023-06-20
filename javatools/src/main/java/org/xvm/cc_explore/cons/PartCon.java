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
    if( _par==null ) {
      _part = (Part)repo.get(name());
    } else {
      Part par = _par.link(repo).link(repo);
      _part = par.child(name());
    }
    assert _part!=null;
    return _part;
  }
  @Override Part part() { assert _part!=null; return _part; }
}
