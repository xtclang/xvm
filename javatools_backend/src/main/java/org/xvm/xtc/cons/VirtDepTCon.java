package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.xtc.ClassPart;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class VirtDepTCon extends DepTCon {
  private final boolean _thisClz;
  private String _name;
  public VirtDepTCon( CPool X ) {
    super(X);
    X.u31();
    _thisClz = X.u1();
  }
  @Override public SB str(SB sb) { return super.str(sb.p(_name)); }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    _name =((StringCon)X.xget())._str;
  }
  @Override ClassPart _part() {
    Part par = _par.part();
    while( !(par instanceof ClassPart clz) )
      par = par._par;
    return (ClassPart)clz.child(_name);
  }
}
