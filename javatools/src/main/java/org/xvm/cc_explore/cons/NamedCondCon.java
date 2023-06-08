package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class NamedCondCon extends CondCon {
  private transient int _namex; // Type index for name
  private StringCon _name;
  
  public NamedCondCon( FilePart X, Format f ) {
    super(f);
    _namex = X.u31();    
  }
  @Override public void resolve( CPool pool ) { _name = (StringCon)pool.get(_namex);  }
}
