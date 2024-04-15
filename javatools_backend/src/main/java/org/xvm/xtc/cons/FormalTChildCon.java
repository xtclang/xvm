package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.*;

/**
  Exploring XEC Constants
 */
public class FormalTChildCon extends PropCon implements ClzCon {
  public FormalTChildCon( CPool X ) { super(X); }
  // Look up the name in the parent class types'
  Part _part() {
    for( PartCon par = _par; !(par instanceof ClassCon clzcon); par = par._par )
      ;
    return clzcon.clz();
  }
  public ClassPart clz() { return (ClassPart)part(); }
}
