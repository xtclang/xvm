package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.collections.Array;

public class Float32 extends BinaryFPNumber {
  public static final Float32 GOLD = new Float32((Never)null);
  public Float32(Never n ) {this(0);}
  public Float32(float f) { _i = f; }
  public final float _i;
  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }
}
