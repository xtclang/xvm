package org.xvm.cc_explore.tvar;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.*;

/** Immutable view over another type
 */
public class TVImmut extends TVar {

  public TVImmut( TVar other ) {
    super(new TVar[]{other});
  }
  
  // -------------------------------------------------------------
  @Override public void _union_impl( TVar tv3) { }

  @Override int _unify_impl(TVar that ) {
    throw XEC.TODO();
  }

  @Override SB _str_impl(SB sb, VBitSet visit, VBitSet dups, boolean debug) {
    return _args[0]._str(sb.p('#'),visit,dups,debug);
  }

}