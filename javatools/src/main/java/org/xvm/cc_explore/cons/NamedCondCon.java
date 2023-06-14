package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class NamedCondCon extends CondCon {
  private StringCon _name;
  
  public NamedCondCon( FilePart X, Format f ) {
    super(f);
    X.u31();    
  }
  @Override public void resolve( FilePart X ) { _name = (StringCon)X.xget();  }
}
