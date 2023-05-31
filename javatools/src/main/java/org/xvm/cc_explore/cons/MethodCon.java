package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class MethodCon extends IdCon {
  private transient int _parx, _sigx, _lamx;  // Type index for parent, signature, lambda
  private MMethodCon _par;
  private SigCon _sig;
  public MethodCon( XEC.XParser X ) throws IOException {
    _parx = X.index();
    _sigx = X.index();
    _lamx = X.index();
  }
  @Override public void resolve( CPool pool ) {
    _par = (MMethodCon)pool.get(_parx);
    _sig = (SigCon)pool.get(_sigx);
  }
}
