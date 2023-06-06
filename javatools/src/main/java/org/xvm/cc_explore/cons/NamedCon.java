package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public abstract class NamedCon extends IdCon {
  private transient int _parx;  // Type index for parent
  private transient int _namex; // Type index for name
  private IdCon _par;
  private StringCon _name;
  NamedCon( FilePart X ) {
    _parx  = X.u31();
    _namex = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _par  = (    IdCon)pool.get( _parx);
    _name = (StringCon)pool.get(_namex);
  }
  @Override public String name() { throw XEC.TODO(); }  
}
