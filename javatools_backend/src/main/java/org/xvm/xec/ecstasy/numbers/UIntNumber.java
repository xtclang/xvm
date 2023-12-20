package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.Ordered;

/**
     Support XTC UIntNumber
*/
public abstract class UIntNumber extends IntNumber {
  public UIntNumber(Never n ) {}
  public UIntNumber() {}

  public static Ordered compare$UIntNumber( XTC gold_type, byte x0, byte x1 ) {
    throw XEC.TODO();
  }  
}
