package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;
import java.io.IOException;

/**
  Exploring XEC Constants
 */
public class ModCon extends IdCon {
  private transient int _tx;    // index for module string name
  private StringCon _str;
  public ModCon( FilePart X ) throws IOException {
    _tx = X.u31();
  }
  @Override public void resolve( CPool pool ) { _str = (StringCon)pool.get(_tx); }
  public String name() { return _str._str; }
}
