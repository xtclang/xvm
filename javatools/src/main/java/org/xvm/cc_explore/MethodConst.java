package org.xvm.cc_explore;

import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class MethodConst extends IdConst {
  private transient int _parx, _sigx, _lamx;  // Type index for parent, signature, lambda
  private MMethodConst _par;
  private SigConst _sig;
  MethodConst( XEC.XParser X ) throws IOException {
    _parx = X.index();
    _sigx = X.index();
    _lamx = X.index();
  }
  @Override void resolve( CPool pool ) {
    _par = (MMethodConst)pool.get(_parx);
    _sig = (SigConst)pool.get(_sigx);
  }
}
