package org.xvm.xec.ecstasy.numbers;

import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xrun.Never;

import java.math.RoundingMode;

/**
     Support XTC Number
*/
public abstract class DecimalFPNumber extends FPNumber {
  public DecimalFPNumber(Never n ) {}
  public DecimalFPNumber() {} 
  public static RoundingMode[] ROUNDINGMODE = new RoundingMode[]{
    RoundingMode.HALF_EVEN, // TiesToEven
    RoundingMode.HALF_EVEN, // TiesToAway
    RoundingMode.CEILING,   // TowardPositive
    RoundingMode.DOWN,      // TowardZero
    RoundingMode.FLOOR      // TowardNegative
  };
}
