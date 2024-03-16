package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.*;

/**
   Same as a Java generic constant name: "Map<K,V>; V get(K key) { ... }"
 */
public class TParmCon extends FormalCon {
  private final int _reg;       // Register index
  public TParmCon( CPool X ) {
    super(X);
    _reg = X.u31();
  }
  @Override Part _part() {
    return new ParmPart((MethodPart)_par.part(),_reg);
  }
}
