package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public abstract class NamedCon extends IdCon {
  private transient int _parx;  // Type index for parent
  private transient int _namex; // Type index for name
  NamedCon( FileComponent X ) throws IOException {
    _parx  = X.u31();
    _namex = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    throw XEC.TODO();
  }  
}
