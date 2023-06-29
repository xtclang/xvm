package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon {
  private IdCon _id;
  public TermTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) { return _id.str(sb.p("# -> ")); }
  @Override public void resolve( CPool X ) { _id = (IdCon)X.xget(); }
  public IdCon id() { return _id; }
  public ClassPart clz() { return (ClassPart)((PartCon)_id).part(); }
  public String name() { return _id.name(); }
  
  @Override public TVar _setype(XEC.ModRepo repo) {
    if( _id instanceof KeywordCon ) return null;
    if( _id instanceof PartCon part )
      return part.link(repo).link(repo).tvar();
    throw XEC.TODO();
  }
}
