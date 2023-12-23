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
  public TCon type() { return _type; }
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    _type.link(repo);
    _formal.link(repo);
    if( _type instanceof ParamTCon pt && pt._parms!=null ) {
      _part = pt._parms[0].link(repo);
      return _part;
    } else {
     _part = _type.link(repo);
    }
    assert _part!=null;
    return _part;
  }
}
