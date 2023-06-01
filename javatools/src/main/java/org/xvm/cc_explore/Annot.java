package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.cons.ClassCon;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class Annot extends Const {
  private transient int _clzx;  // Type index for clazz
  private transient int[] _parmxs; // Type index for each parameter
  ClassCon _clz;
  Const[] _cons;
  Annot( FileComponent X ) throws IOException {
    _clzx = X.u31();
    _parmxs = X.idxAry();
  }
  @Override public void resolve( CPool pool ) {
    _clz = (ClassCon)pool.get(_clzx);
    _cons = new Const[_parmxs.length];
    for( int i=0; i<_parmxs.length; i++ )
      _cons[i] = pool.get(_parmxs[i]);
  }
}
