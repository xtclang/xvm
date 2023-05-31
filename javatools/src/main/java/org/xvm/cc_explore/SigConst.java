package org.xvm.cc_explore;

import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class SigConst extends IdConst {
  private transient int _namex;  // Type index for name
  private transient int[] _parmxs, _retxs;  // Type index arrays for parms, returns
  
  SigConst( XEC.XParser X ) throws IOException {
    _namex  = X.index();
    _parmxs = X.idxAry();
    _retxs  = X.idxAry();
  }
  @Override void resolve( CPool pool ) {
    throw XEC.TODO();
  }  
}
