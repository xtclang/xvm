package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.io.IOException;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon {
  private transient int _defx;  // Type index for def
  Const _id;
  public TermTCon( FilePart X ) throws IOException {
    _defx = X.u31();
  }
  @Override public void resolve( CPool pool ) { _id = pool.get(_defx); }
  @Override public Const resolveTypedefs() { throw XEC.TODO(); }
}
