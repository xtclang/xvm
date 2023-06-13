package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MethodCon extends IdCon {
  private transient int _parx, _sigx, _lamx;  // Type index for parent, signature, lambda
  private MMethodCon _par;
  private SigCon _sig;
  public MethodCon( FilePart X ) {
    _parx = X.u31();
    _sigx = X.u31();
    _lamx = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _par = (MMethodCon)pool.get(_parx);
    _sig = (SigCon)pool.get(_sigx);
  }
  @Override public String name() { return _par.name(); }
  public TCon[] rawRets () { return _sig.rawRets (); }
  public TCon[] rawParms() { return _sig.rawParms(); }
}
