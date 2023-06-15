package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.CPool;

/**
  Exploring XEC Constants
 */
public class ModCon extends IdCon {
  private String _str;
  public ModCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _str =((StringCon)X.xget())._str; }
  @Override public String name() { return _str; }
  @Override public String toString() { return name(); }
}
