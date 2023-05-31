package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
 */
public class SigCon extends IdCon {
  private transient int _namex;  // Type index for name
  private transient int[] _parmxs, _retxs;  // Type index arrays for parms, returns
  
  public SigCon( XEC.XParser X ) throws IOException {
    _namex  = X.index();
    _parmxs = X.idxAry();
    _retxs  = X.idxAry();
  }
  @Override public void resolve( CPool pool ) {
    throw XEC.TODO();
  }  
}
