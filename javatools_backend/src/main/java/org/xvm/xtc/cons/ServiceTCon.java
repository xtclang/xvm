package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.xtc.ClassPart;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class ServiceTCon extends TCon implements ClzCon {
  private TCon _con;
  private ClassPart _part;
  public ServiceTCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  @Override public ClassPart clz() {
    if( _part != null ) throw XEC.TODO();
    return _part;
  }
}
