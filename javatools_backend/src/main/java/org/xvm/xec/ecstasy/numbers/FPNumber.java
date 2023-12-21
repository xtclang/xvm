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

  /**
   * Options for rounding.
   *
   * These are the rounding directions defined by the IEEE 754 standard.
   */
  enum Rounding {TiesToEven, TiesToAway, TowardPositive, TowardZero, TowardNegative}
}
