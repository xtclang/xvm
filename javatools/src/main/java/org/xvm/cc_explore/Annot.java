package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.ClassCon;

/**
  Exploring XEC Constants
 */
public class Annot extends Const {
  ClassCon _clz;
  Const[] _cons;
  Annot( CPool X ) {
    X.u31();
    X.skipAry();
  }
  @Override public void resolve( CPool X ) {
    _clz = (ClassCon)X.xget();
    _cons = X.consts();
  }
  
  // Parse an array of Annots from a pre-filled constant pool
  public static Annot[] xannos( CPool X ) {
    Annot[] as = new Annot[X.u31()];
    for( int i=0; i<as.length; i++ )  as[i] = (Annot)X.xget();
    return as;
  }
  
  @Override public String toString() { return _clz.toString(); }
}
