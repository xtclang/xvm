package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;
import org.xvm.cc_explore.tvar.TVLeaf;

/**
  Exploring XEC Constants
 */
public class RecurTCon extends TermTCon {
  public RecurTCon( CPool X ) { super(X); }
  @Override TVar _setype() {
    setype_stop_cycles( new TVLeaf() );
    TVar tv = ((PartCon)_id).setype();
    tvar().unify(tv);
    return tv;
  }
}
