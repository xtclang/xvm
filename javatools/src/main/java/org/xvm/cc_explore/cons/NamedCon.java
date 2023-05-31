package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public abstract class NamedCon extends IdCon {
  private transient int _parx;  // Type index for parent
  private transient int _namex; // Type index for name
  NamedCon( XEC.XParser X ) throws IOException {
    _parx  = X.index();
    _namex = X.index();
  }
  @Override public void resolve( CPool pool ) {
    throw XEC.TODO();
  }  
}
