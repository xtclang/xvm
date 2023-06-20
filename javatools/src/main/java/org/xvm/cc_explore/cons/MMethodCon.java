package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MMethodCon extends NamedCon {
  public MMethodCon( CPool X ) { super(X); }
  @Override public Part link( XEC.ModRepo repo ) {
    Part p = super.link(repo);
    if( p!=null ) return p;
    // If parent lookup fails, try again in Object
    p = repo.get("ecstasy.xtclang.org").child("Object",repo).child(name(),repo);
    assert p!=null;
    return (_part=p);    
  }
}
