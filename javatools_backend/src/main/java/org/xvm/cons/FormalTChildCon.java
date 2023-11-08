package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class FormalTChildCon extends PropCon {
  public FormalTChildCon( CPool X ) { super(X); }
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    // Link the parent, do any replacement lookups
    Part par = _par.link(repo).link(repo);
    // Now find self in Class parent, which is probably far up the parent chain
    while( !(par instanceof ClassPart clz) ) par = par._par;
    Part p = clz.child(_name);
    if( p==null ) p = par; // Big Lie
    return (_part=p);
  }
}
