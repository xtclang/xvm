package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.collections.Array;

public class Float64 extends BinaryFPNumber {
  public static final Float64 GOLD = new Float64((Never)null);
  public Float64(Never n ) {this(0);}
  public Float64(double d) { _i = d; }
  public final double _i;
  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }
}
