package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;
import java.io.IOException;

/**
  Exploring XEC Constants
 */
public class LitCon extends Const {
  final Format _f;
  private transient int _x;     // Index for actual const
  StringCon _str;               // The actual string constant
  public LitCon( FilePart X, Format f ) throws IOException {
    _f = f;
    _x = X.u31();
  }

  @Override public void resolve( CPool pool ) {
    _str = (StringCon)pool.get(_x);
  }

}
