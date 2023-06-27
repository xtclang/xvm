package org.xvm.cc_explore.tvar;

public class TVLeaf extends TVar {

  // -------------------------------------------------------------
  @Override int _unify_impl(TVar that ) {
    // Always fold leaf into the other.
    // If that is ALSO a Leaf, keep the lowest UID.
    assert !(that instanceof TVLeaf) || _uid > that._uid;
    // Leafs must call union themselves; other callers of _unify_impl get a
    // union call done for them.
    return this.union(that);
  }
  
  // Leafs have no subclass specific parts to union.
  @Override public void _union_impl(TVar that) { }

  // Always maybe unifies
  @Override int _trial_unify_ok_impl( TVar tv3 ) { return 0; }
}
