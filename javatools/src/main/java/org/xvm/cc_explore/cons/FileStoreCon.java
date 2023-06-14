package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class FileStoreCon extends TCon {
  StringCon _path;
  FSNodeCon _dir;
  public FileStoreCon( FilePart X ) {
    X.u31();
    X.u31();
  }
  @Override public void resolve( FilePart X ) {
    _path = (StringCon)X.xget();
    _dir  = (FSNodeCon)X.xget();
  }
}
