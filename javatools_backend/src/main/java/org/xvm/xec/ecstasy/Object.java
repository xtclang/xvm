package org.xvm.xec.ecstasy;

import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xtc.*;

// Not-The-Java Object.
public interface Object extends Comparable {
  // The fully dynamic equals lookup, based on the given compile-time class
  static boolean equals( XTC gold_type, XTC x0, XTC x1 ) {
    return gold_type.equals(x0,x1); // Dynamic against gold
  }

  
  static boolean equals$Object( XTC gold_type, XTC x0, XTC x1 ) {
    return x0==x1;              // Purely pointer equality
  }

}
