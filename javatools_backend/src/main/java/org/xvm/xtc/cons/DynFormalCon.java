package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

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
    _reg  = X.u31();
    _nreg = X.u31();
    X.u31();
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    X.u31();
    X.u31();
    _type   = (     TCon)X.xget();
    _formal = (FormalCon)X.xget();
  }
  public TCon type() { return _type; }
  @Override Part _part() {
    if( _type instanceof ParamTCon pt && pt._parms!=null )
      return _formal.part();
  //   _part = _type.link(repo);
    throw XEC.TODO();
  }
}
