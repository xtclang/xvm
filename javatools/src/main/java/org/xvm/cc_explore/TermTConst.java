package org.xvm.cc_explore;

import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class TermTConst extends TConst {
  private transient int _defx;  // Type index for def
  TermTConst( XEC.XParser X ) throws IOException {
    _defx = X.index();
  }
  @Override void resolve( CPool pool ) {
    throw XEC.TODO();
  }
}
