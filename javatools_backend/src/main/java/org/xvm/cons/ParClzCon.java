package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class ParClzCon extends PartCon {
  private PartCon _child;
  public ParClzCon( CPool X ) { X.u31();  }
  @Override public void resolve( CPool X ) { _child = (PartCon)X.xget(); }
  @Override public String name() { return _child.name(); }
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    assert _par==null;
    return (_part = _child.link(repo));
  }
}
