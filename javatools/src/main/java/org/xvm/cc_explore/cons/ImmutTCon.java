package org.xvm.cc_explore;

import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class ImmutTConst extends TConst {
  private transient int _tx;  // Type index for type
  ImmutTConst( XEC.XParser X ) throws IOException {
    _tx = X.index();
  }
  @Override void resolve( CPool pool ) {
    throw XEC.TODO();
  }
}
