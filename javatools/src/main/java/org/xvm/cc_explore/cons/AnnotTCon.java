package org.xvm.cc_explore;

import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class AnnotTConst extends TConst {
  private transient int _anox, _tx;  // Type index for annotation, type
  AnnotTConst( XEC.XParser X ) throws IOException {
    _anox = X.index();
    _tx = X.index();
  }
  @Override void resolve( CPool pool ) {
    throw XEC.TODO();
  }
}
