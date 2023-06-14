package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public abstract class NamedCon extends IdCon {
  private IdCon _par;
  private StringCon _name;
  NamedCon( FilePart X ) {
    X.u31();
    X.u31();
  }
  @Override public void resolve( FilePart X ) {
    _par  = (    IdCon)X.xget();
    _name = (StringCon)X.xget();
  }
  @Override public String name() { return _name._str; }
  @Override public String toString() { return name(); }
}
