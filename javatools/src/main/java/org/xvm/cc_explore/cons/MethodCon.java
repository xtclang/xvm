package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class MethodCon extends IdCon {
  private transient int _parx, _sigx, _lamx;  // Type index for parent, signature, lambda
  private MMethodCon _par;
  private SigCon _sig;
  public MethodCon( FileComponent X ) throws IOException {
    _parx = X.u31();
    _sigx = X.u31();
    _lamx = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _par = (MMethodCon)pool.get(_parx);
    _sig = (SigCon)pool.get(_sigx);
  }
  @Override public Const resolveTypedefs() {
    throw XEC.TODO();
  }
}
