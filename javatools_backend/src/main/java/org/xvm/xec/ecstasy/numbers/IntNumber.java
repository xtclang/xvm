package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xrun.Never;

/**
     Support XTC IntNumber
*/
public abstract class IntNumber extends Number {
  public IntNumber(Never n ) {}
  public IntNumber() {}

  public static Int128 toInt128(long x) { return new Int128(x); }
  public static long estimateStringLength(long x) { throw XEC.TODO(); }

  public static boolean  equals$IntNumber(IntNumber gold, IntNumber x, IntNumber y) { return gold.equals (x,y); }
  public static Ordered compare$IntNumber(IntNumber gold, IntNumber x, IntNumber y) { return gold.compare(x,y); }
}
