package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.ClassCon;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class Annot extends PartCon {
  private Const[] _cons;
  public Annot( CPool X ) {
    X.u31();
    X.skipAry();
  }
  @Override public String name() { throw XEC.TODO(); }
  @Override public SB str(SB sb) { return _par.str(sb.p("@ -> ")); }
  @Override public void resolve( CPool X ) {
    _par = (ClassCon)X.xget();
    _cons = X.consts();
  }
  @Override public Part link(XEC.ModRepo repo) {
    if( _part!=null ) return _part;
    _part = _par.link(repo);
    if( _cons!=null )
      for( Const con : _cons )
        con.con_link(repo);
    return _part;
  }
  
  // Parse an array of Annots from a pre-filled constant pool
  public static Annot[] xannos( CPool X ) {
    int len = X.u31();
    if( len==0 ) return null;
    Annot[] as = new Annot[len];
    for( int i=0; i<len; i++ )  as[i] = (Annot)X.xget();
    return as;
  }
  
}
