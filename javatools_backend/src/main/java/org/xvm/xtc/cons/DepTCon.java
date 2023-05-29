package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.xtc.ClassPart;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public abstract class DepTCon extends TCon implements ClzCon {
  public TCon _par;
  public ClassPart _part;
  DepTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) {
    sb.p("<dep>");
    return _par==null ? sb : _par.str(sb.p(" -> "));
  }
  @Override public ClassPart clz () { return part(); }
  @Override public ClassPart part() {
    return _part==null ? (_part = _part()) : _part;
  }
  abstract ClassPart _part();
  
  @Override public void resolve( CPool X ) { _par = (TCon)X.xget(); }
}
