package org.xvm.xec.ecstasy.numbers;

import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xrun.Never;

/**
     Support XTC Number
*/
public abstract class FPNumber extends Number {
  public FPNumber(Never n ) {}
  public FPNumber() {}
  public static final FPLiteral PI = new FPLiteral("3.141592653589793238462643383279502884197169399375105820974944592307816406286");

  /**
   * Options for rounding.
   *
   * These are the rounding directions defined by the IEEE 754 standard.
   */
  public enum Rounding {TiesToEven, TiesToAway, TowardPositive, TowardZero, TowardNegative}
}
