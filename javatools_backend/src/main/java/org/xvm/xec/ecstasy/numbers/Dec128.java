package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.ecstasy.collections.Array;
import org.xvm.xec.ecstasy.collections.AryXTC;
import org.xvm.xec.ecstasy.numbers.Bit;
import org.xvm.xec.ecstasy.numbers.FPNumber.Rounding;
import org.xvm.xrun.Never;


/**
     Support XTC Number
*/
public class Dec128 extends DecimalFPNumber {
  public static final Dec128 GOLD = new Dec128((Never)null);
  public Dec128(Never n ) { }
  public Dec128 mul( Dec128 x ) { throw XEC.TODO(); }
  public static Int128 toInt128(boolean foo, Rounding round) { throw XEC.TODO(); }
  public AryXTC<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }
}
