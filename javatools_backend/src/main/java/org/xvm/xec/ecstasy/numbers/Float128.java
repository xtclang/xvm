package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.collections.Array;

public class Float128 extends BinaryFPNumber {
  public Float128(Never n ) {}
  public Float128() {}
  Array<Bit> toBitArray(Array.Mutability mut) { throw XEC.TODO(); }
}
