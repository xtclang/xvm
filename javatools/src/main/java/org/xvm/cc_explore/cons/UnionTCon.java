package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;

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
