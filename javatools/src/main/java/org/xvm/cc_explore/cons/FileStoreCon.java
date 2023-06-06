package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class FileStoreCon extends TCon {
  private final transient int _pathx, _dirx;  // Type index for annotation, type
  StringCon _path;
  FSNodeCon _dir;
  public FileStoreCon( FilePart X ) {
    _pathx = X.u31();
    _dirx  = X.u31();
  }
  @Override public void resolve( CPool pool ) {
    _path = (StringCon)pool.get(_pathx);
    _dir  = (FSNodeCon)pool.get(_dirx);
  }
}
