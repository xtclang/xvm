package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class ModCon extends IdCon {
  private StringCon _str;
  public ModCon( FilePart X ) { X.u31(); }
  public String name() { return _str._str; }
  @Override public String toString() { return name(); }
  @Override public void resolve( FilePart X ) { _str = (StringCon)X.xget(); }
}
