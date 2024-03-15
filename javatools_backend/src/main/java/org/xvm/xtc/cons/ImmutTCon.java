package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.ClassPart;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class ImmutTCon extends TCon implements ClzCon {
  TCon _con;
  private Part _part;
  public ImmutTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) { sb.p("R/O");  return _con==null ? sb : _con.str(sb.p(" -> "));  }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
  public TCon icon() { return _con; }
  @Override public ClassPart clz() {
    throw XEC.TODO(); // From the part instead of con?
    //return ((ClzCon)_con).clz();
  }
}
