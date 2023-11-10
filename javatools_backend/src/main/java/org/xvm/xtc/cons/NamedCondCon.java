package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

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
