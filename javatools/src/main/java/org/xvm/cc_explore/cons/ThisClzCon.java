package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class ThisClzCon extends PartCon {
  private IdCon _clz;
  public ThisClzCon( CPool X ) { X.u31();  }
  @Override public void resolve( CPool X ) { _clz = (ClassCon)X.xget(); }
  @Override public String name() { return _clz.name(); }
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    assert _par==null;
    return (_part = _clz.link(repo));
  }
}
