package org.xvm.cc_explore;

import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public abstract class NamedConst extends IdConst {
  private transient int _parx;  // Type index for parent
  private transient int _namex; // Type index for name
  NamedConst( XEC.XParser X ) throws IOException {
    _parx  = X.index();
    _namex = X.index();
  }
  @Override void resolve( CPool pool ) {
    throw XEC.TODO();
  }  
}
