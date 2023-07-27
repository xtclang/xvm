package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;

/**
  General Con class backed by matching Part class
 */
public abstract class PartCon extends TCon implements IdCon {
  PartCon _par;                 // Parent
  Part _part;
  @Override public SB str(SB sb) { return _par==null ? sb : _par.str(sb.p(" -> ")); }

  @Override public Part link( XEC.ModRepo repo ) { return partlink(repo); }
  Part partlink( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    // Link the parent, do any replacement lookups
    Part par = _par.link(repo).link(repo);
    // Find the child in the parent
    return (_part = par.child(name())).link(repo);
  }
  @Override TVar _setype() { return _part.setype(); }

  @Override public Part part() { assert _part!=null; return _part; }

  // Parse an array of Const from a pre-filled constant pool
  public static PartCon[] parts( CPool X ) {
    int len = X.u31();
    if( len==0 ) return null;
    PartCon[] as = new PartCon[len];
    for( int i=0; i<len; i++ )  as[i] = (PartCon)X.xget();
    return as;
  }

}
