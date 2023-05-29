package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.RelPart;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class UnionTCon extends RelTCon {
  public UnionTCon( CPool X ) { super(X); }
  @Override public SB str(SB sb) {
    if( _con2 instanceof TermTCon tt ) sb.p(tt.name());
    sb.p("|");
    return _con1.str(sb);
  }
  @Override RelPart.Op op() { return RelPart.Op.Union; }
}
