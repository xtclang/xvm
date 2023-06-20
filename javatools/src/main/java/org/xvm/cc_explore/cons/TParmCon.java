package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.TLS;

/**
   Same as a Java generic constant name: "Map<K,V>; <V> get(K key) { ... }"
 */
public class TParmCon extends FormalCon {
  private final int _reg;       // Register index
  private final TLS<Boolean> _tlsReEntry = new TLS<>();
  public TParmCon( CPool X ) {
    super(X);
    _reg = X.u31();
  }
}
