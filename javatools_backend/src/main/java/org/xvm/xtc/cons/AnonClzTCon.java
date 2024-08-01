package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;
import org.xvm.xtc.ClassPart;

/**
  Exploring XEC Constants
 */
public class AnonClzTCon extends DepTCon {
  private ClassCon _anon;
  public AnonClzTCon( CPool X ) { super(X); X.u31(); }
  @Override public void resolve( CPool X ) { super.resolve(X); _anon = (ClassCon)X.xget(); }
  @Override ClassPart _part() { return _anon.clz(); }
}
