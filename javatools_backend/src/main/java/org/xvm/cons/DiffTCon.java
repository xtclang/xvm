package org.xvm.cons;

import org.xvm.CPool;
import org.xvm.RelPart;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class DiffTCon extends RelTCon {
  public DiffTCon( CPool X ) { super(X); }
  @Override public SB str(SB sb) {
    if( _con2 instanceof TermTCon tt ) sb.p(tt.name());
    sb.p("-");
    return _con1.str(sb);
  }
  @Override RelPart.Op op() { return RelPart.Op.Difference; }
}
