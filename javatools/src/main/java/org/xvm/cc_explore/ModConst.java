package org.xvm.cc_explore;

import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class ModConst extends IdConst {
  private transient int _tx;    // index for module string name
  private StringConst _str;
  ModConst( XEC.XParser X ) throws IOException {
    _tx = X.index();
  }
  @Override void resolve( CPool pool ) { _str = (StringConst)pool.get(_tx); }
  String name() { return _str._str; }
}
