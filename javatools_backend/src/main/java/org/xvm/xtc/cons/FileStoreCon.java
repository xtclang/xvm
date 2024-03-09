package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;
import org.xvm.XEC;
import org.xvm.xtc.Part;

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
