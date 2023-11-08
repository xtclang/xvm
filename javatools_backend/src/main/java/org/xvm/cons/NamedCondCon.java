package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class NamedCondCon extends CondCon {
  private String _name;
  
  public NamedCondCon( CPool X, Format f ) {
    super(f);
    X.u31();    
  }
  @Override public void resolve( CPool X ) { _name =((StringCon)X.xget())._str; }
}
