package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xec.ecstasy.collections.Array;

/**
     Support XTC IntN
*/
public class IntN extends IntNumber {
  public static final IntN GOLD = new IntN(null);
  public IntN(Never n ) { } // No-arg constructor
  
  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }

  @Override public long hashCode( XTC x ) { throw XEC.TODO(); }
  @Override public boolean equals ( XTC x0, XTC x1 ) { throw XEC.TODO(); }
  @Override public Ordered compare( XTC x0, XTC x1 ) { throw XEC.TODO(); }

  @Override public final String toString() { throw XEC.TODO(); }
  @Override public long estimateStringLength() { return 128; }
}
