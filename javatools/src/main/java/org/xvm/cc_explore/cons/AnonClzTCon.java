package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class AnonClzTCon extends DepTCon {
  private final transient int _anonx;
  private ClassCon _anon;
  public AnonClzTCon( FilePart X ) { super(X); _anonx = X.u31(); }
  @Override public void resolve( CPool pool ) { super.resolve(pool); _anon = (ClassCon)pool.get(_anonx); }
}
