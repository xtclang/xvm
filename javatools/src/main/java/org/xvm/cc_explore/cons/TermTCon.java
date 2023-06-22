package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon {
  private IdCon _id;
  private XType _type;
  public TermTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) { return _id.str(sb.p("# -> ")); }
  @Override public void resolve( CPool X ) { _id = (IdCon)X.xget(); }
  public IdCon id() { return _id; }
  public String name() { return _id.name(); }
  @Override public XType link(XEC.ModRepo repo) {
    if( _type != null ) return _type;
    if( _id instanceof PartCon part ) return (_type = (XType)part.link(repo));
    if( _id instanceof KeywordCon ) return null;
    throw XEC.TODO();
  }
  @Override XType part() { assert _type!=null; return _type; }
}
