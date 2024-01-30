package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.ecstasy.collections.Array;
import org.xvm.xec.ecstasy.collections.AryXTC;
import org.xvm.xec.ecstasy.numbers.Bit;
import org.xvm.xrun.Never;

/**
     Support XTC Number
*/
public class Dec128 extends DecimalFPNumber {
  public static final Dec128 GOLD = new Dec128((Never)null);
  public Dec128(Never n ) { }
  public AryXTC<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }
}
