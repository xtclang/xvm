package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.Const;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class Annot extends Const {
  private transient int _clzx;  // Type index for clazz
  private transient int[] _parmxs; // Type index for each parameter
  Annot( XEC.XParser X ) throws IOException {
    _clzx = X.index();
    _parmxs = X.idxAry();
  }
  @Override public void resolve( CPool pool ) {
    throw XEC.TODO();
  }
}
