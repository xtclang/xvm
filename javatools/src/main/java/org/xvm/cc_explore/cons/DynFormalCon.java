package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class DynFormalCon extends FormalCon {
  // The register unique id within the enclosing method (not used during the compilation).
  private final int _reg, _nreg; // Registers
  private TCon _type;
  private FormalCon _formal;
  public DynFormalCon( CPool X ) {
    super(X);
    _reg  = X.u16();
    _nreg = X.u16();
    X.u31();
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    X.u16();
    X.u16();
    _type   = (     TCon)X.xget();
    _formal = (FormalCon)X.xget();
  }  
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    return (_part=_formal.link(repo));
  }
}
