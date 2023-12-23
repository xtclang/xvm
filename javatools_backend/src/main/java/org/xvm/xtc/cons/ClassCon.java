package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;
import org.xvm.xtc.ClassPart;

/**
  Exploring XEC Constants
 */
public class ClassCon extends NamedCon implements ClzCon {
  public ClassCon( CPool X ) { super(X); }
  @Override public ClassPart clz() { return (ClassPart)_part; }
}
