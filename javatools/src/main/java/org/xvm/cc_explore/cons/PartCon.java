package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  General Con class backed by matching Part class
 */
public abstract class PartCon<PART extends Part> extends IdCon {
  PartCon _par;                 // Parent
  private PART _part;
  @Override public PART link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    if( _par==null ) {
      _part = (PART)repo.get(name());
    } else {
      Part par = _par.link(repo).link(repo);
      _part = (PART)par.child(name());
    }
    assert _part!=null;
    return _part;
  }
  @Override PART part() { assert _part!=null; return _part; }
}
