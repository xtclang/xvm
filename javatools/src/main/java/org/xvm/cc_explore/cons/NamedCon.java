package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public abstract class NamedCon extends IdCon {
  private IdCon _par;
  private String _name;
  NamedCon( CPool X ) {
    X.u31();
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    _par  = (    IdCon)X.xget();
    _name =((StringCon)X.xget())._str;
  }
  @Override public String name() { return _name; }
  @Override public String toString() { return _name; }
}
