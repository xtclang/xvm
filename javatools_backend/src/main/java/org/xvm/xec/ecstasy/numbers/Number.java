package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xec.ecstasy.collections.Array;
import org.xvm.xrun.Never;

/**
     Support XTC Number
*/
public abstract class Number extends Const implements Orderable {
  public Number(Never n ) {}
  public Number() {}

  public static boolean equals$Number( XTC gold, Number n0, Number n1 ) {
    if( n0.getClass()==n1.getClass() )
      return n0.equals(n1);     // Same class, normal dispatch is ok
    // Widening rules for Number equality
    throw XEC.TODO();
  }
  public static boolean equals$Number( XTC gold, double n0, double n1 ) { return n0==n1; }

  abstract Array<Bit> toBitArray(Array.Mutability mut);

  public Signum sign$get() { throw XEC.TODO(); }

  public enum Signum {
    Negative("-", -1, Ordered.Lesser ),
    Zero    ("" ,  0, Ordered.Equal  ),
    Positive("+", +1, Ordered.Greater);
    public final String prefix;
    public final int factor;
    public final Ordered ordered;
    private Signum( String pre, int fact, Ordered ord) {
      prefix = pre;
      factor = fact;
      ordered= ord;
    }
  }


}
