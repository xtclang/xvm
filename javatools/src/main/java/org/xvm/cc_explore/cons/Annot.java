package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.ClassCon;

/**
  Exploring XEC Constants
 */
public class Annot extends Const {
  private ClassCon _con;
  private Const[] _cons;
  private ClassPart _clz;
  public Annot( CPool X ) {
    X.u31();
    X.skipAry();
  }
  @Override public String toString() { return _con.toString(); }
  @Override public void resolve( CPool X ) {
    _con = (ClassCon)X.xget();
    _cons = X.consts();
  }
  @Override public Part link(XEC.ModRepo repo) {
    if( _clz!=null ) return _clz;
    _clz = _con.link(repo);
    for( Const con : _cons )
      con.link(repo);
    return _clz;
  }
  
  // Parse an array of Annots from a pre-filled constant pool
  public static Annot[] xannos( CPool X ) {
    Annot[] as = new Annot[X.u31()];
    for( int i=0; i<as.length; i++ )  as[i] = (Annot)X.xget();
    return as;
  }
  
}
