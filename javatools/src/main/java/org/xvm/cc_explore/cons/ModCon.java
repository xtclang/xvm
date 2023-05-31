package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class ModCon extends IdCon {
  private transient int _tx;    // index for module string name
  private StringCon _str;
  public ModCon( XEC.XParser X ) throws IOException {
    _tx = X.index();
  }
  @Override public void resolve( CPool pool ) { _str = (StringCon)pool.get(_tx); }
  public String name() { return _str._str; }
}
