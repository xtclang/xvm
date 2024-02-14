package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xrun.Never;

/**
     Support XTC IntNumber
*/
public abstract class IntNumber extends Number {
  public IntNumber(Never n ) {}
  public IntNumber() {}

  public static Int128 toInt128(long x) { return new Int128(x); }
  public static long estimateStringLength(long x) { throw XEC.TODO(); }
}
