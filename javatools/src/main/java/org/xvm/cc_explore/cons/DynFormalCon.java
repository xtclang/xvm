package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class DynFormalCon extends FormalCon {
  // The register unique id within the enclosing method (not used during the compilation).
  private final int _reg, _nreg; // Registers
  private final transient int _typex, _formx;
  private TCon _type;
  private FormalCon _formal;
  public DynFormalCon( FilePart X ) {
    super(X);
    _reg = X.u16();
    _nreg = X.u16();
    _typex = X.u31();
    _formx = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    super.resolve(pool);
    _type   = (     TCon)pool.get(_typex);
    _formal = (FormalCon)pool.get(_formx);
  }  
}
