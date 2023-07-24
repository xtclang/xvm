package org.xvm.cc_explore.tvar;

import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.*;
import org.xvm.cc_explore.XEC;

/** A ground term 
 */
public class TVMiss extends TVar {
  
  public TVMiss( ) { }
  
  // -------------------------------------------------------------
  @Override public void _union_impl( TVar tv3) {
    throw XEC.TODO();
  }

  @Override int _unify_impl(TVar that ) {
    throw XEC.TODO();
  }

  // -------------------------------------------------------------
  // Sub-classes specify trial_unify on sub-parts.
  // Check arguments, not return nor omem.
  @Override boolean _trial_unify_ok_impl( TVar tv3 ) {
    throw XEC.TODO();
  }

  @Override SB _str_impl(SB sb, VBitSet visit, VBitSet dups, boolean debug) { return sb.p("miss"); }

}
