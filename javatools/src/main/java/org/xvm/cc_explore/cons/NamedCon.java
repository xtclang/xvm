package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public abstract class NamedCon extends PartCon {
  String _name;
  NamedCon( CPool X ) {  X.u31(); X.u31(); }
  @Override public String name() { return _name; }
  @Override public String toString() { return _name; }
  @Override public void resolve( CPool X ) {
    _par  = (PartCon)X.xget();  // Parent is parsed before name
    _name =((StringCon)X.xget())._str;
  }
}
