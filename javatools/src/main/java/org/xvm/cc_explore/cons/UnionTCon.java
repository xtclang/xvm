package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.Part;
import org.xvm.cc_explore.XEC;
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
  @Override public TVar _setype(XEC.ModRepo repo) {
    super._setype(repo);
  //  TVar tv1 = _con1.tvar();
  //  TVar tv2 = _con2.tvar();
    throw XEC.TODO();
  }
}
