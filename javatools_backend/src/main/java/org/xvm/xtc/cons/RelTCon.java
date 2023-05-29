package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.RelPart;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public abstract class RelTCon extends TCon implements ClzCon {
  public TCon  _con1,  _con2;
  RelPart _part;
  public RelTCon( CPool X ) {
    X.u31();
    X.u31();
  }
  
  @Override public RelPart clz() { assert _part!=null; return _part; }
  
  @Override public void resolve( CPool X ) {
    _con1 = (TCon)X.xget();
    _con2 = (TCon)X.xget();
  }

  abstract RelPart.Op op();
}
