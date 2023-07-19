package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.RelPart;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;

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
