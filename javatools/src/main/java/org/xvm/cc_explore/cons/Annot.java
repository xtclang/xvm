package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.ClassCon;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class Annot extends Const {
  ClassCon _con;
  private Const[] _cons;
  private ClassPart _clz;
  public Annot( CPool X ) {
    X.u31();
    X.skipAry();
  }
  @Override public SB str(SB sb) { return _con.str(sb.p("@ -> ")); }
  @Override public void resolve( CPool X ) {
    _con = (ClassCon)X.xget();
    _cons = X.consts();
  }
  @Override public Part link(XEC.ModRepo repo) {
    if( _clz!=null ) return _clz;
    _clz = (ClassPart)_con.link(repo);
    if( _cons!=null )
      for( Const con : _cons )
        con.link(repo);
    return _clz;
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
