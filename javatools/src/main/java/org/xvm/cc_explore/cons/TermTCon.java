package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon implements ClzCon {
  Const _id;
  private Part _part;
  public TermTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) { return _id.str(sb.p("# -> ")); }
  @Override public void resolve( CPool X ) { _id = X.xget(); }
  public Const id() { return _id; }
  @Override public ClassPart clz() { return (ClassPart)_part; }
  public String name() { return ((IdCon)_id).name(); }
  
  @Override public Part link(XEC.ModRepo repo) {
    return _part==null ? (_part=_id.link(repo)) : _part;
  }
  @Override public TVar _setype(XEC.ModRepo repo) {
    if( _id instanceof KeywordCon ) return null;
    if( _id instanceof PartCon part )
      return part.link(repo).link(repo).tvar();
    throw XEC.TODO();
  }
}
