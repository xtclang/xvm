package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.CPool;

/**
  Exploring XEC Constants
 */
public class FileStoreCon extends TCon {
  String _path;
  FSNodeCon _dir;
  public FileStoreCon( CPool X ) {
    X.u31();
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    _path =((StringCon)X.xget())._str;
    _dir  = (FSNodeCon)X.xget();
  }
}
