package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.ClassCon;

/**
  Exploring XEC Constants
 */
public class Annot extends Const {
  private transient int _clzx;  // Type index for clazz
  private transient int[] _parmxs; // Type index for each parameter
  ClassCon _clz;
  Const[] _cons;
  Annot( FilePart X ) {
    _clzx = X.u31();
    _parmxs = X.idxAry();
  }
  @Override public void resolve( CPool pool ) {
    _clz = (ClassCon)pool.get(_clzx);
    _cons = resolveAry(pool,_parmxs);
  }
  
  // Parse an array of Annots from a pre-filled constant pool
  public static Annot[] xannos( FilePart X ) {
    Annot[] as = new Annot[X.u31()];
    for( int i=0; i<as.length; i++ )  as[i] = (Annot)X.xget();
    return as;
  }
  
  @Override public String toString() { return _clz.toString(); }
}
