package org.xvm.cc_explore.tvar;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.*;

/** Immutable view over another type
 */
public class TVImmut extends TVar {

  public TVImmut( TVar other ) {
    super( other );
    assert other instanceof TVLeaf || other instanceof TVStruct;
  }

  
  // -------------------------------------------------------------
  @Override public void _union_impl( TVar tv3) { }

  @Override int _unify_impl(TVar that ) {
    throw XEC.TODO();
  }

  @Override int _fresh_unify_impl( TVar tv ) {
    assert !unified() && !tv.unified();
    TVStruct that = (TVStruct)tv; // Invariant when called
    // Have this/Fresh: I -> S   and  that/TV  LS (Leaf or Struct)
    // Want LS>> ( S.fresh_unify(LS.clone)
    TVar cp = that.copy();
    TVImmut tv2 = new TVImmut(cp);
    that._uf = tv2;
    return arg(0)._fresh_unify(cp);
  }
  
  @Override SB _str_impl(SB sb, VBitSet visit, VBitSet dups, boolean debug) {
    return _args[0]._str(sb.p('#'),visit,dups,debug);
  }

}
