package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class ImmutTCon extends TCon {
  private transient int _tx;  // Type index for type
  TCon _icon;
  public ImmutTCon( XEC.XParser X ) throws IOException {
    _tx = X.index();
  }
  @Override public void resolve( CPool pool ) {
    _icon = (TCon)pool.get(_tx);
  }
}
