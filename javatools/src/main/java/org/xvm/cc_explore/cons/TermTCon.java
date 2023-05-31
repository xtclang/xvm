package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon {
  private transient int _defx;  // Type index for def
  public TermTCon( XEC.XParser X ) throws IOException {
    _defx = X.index();
  }
  @Override public void resolve( CPool pool ) {
    throw XEC.TODO();
  }
}
