package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon {
  private IdCon _id;
  private Part _part;
  public TermTCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _id = (IdCon)X.xget(); }
  public IdCon id() { return _id; }
  @Override public Part link(XEC.ModRepo repo) {
    if( _part != null ) return _part;
    if( _id instanceof PartCon part ) return (_part = part.link(repo));
    if( _id instanceof KeywordCon ) return null;
    throw XEC.TODO();
  }
}
