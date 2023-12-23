package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xrun.Never;

/**
     Support XTC Number
*/
public abstract class Number extends Const implements Orderable {
  public Number(Never n ) {}
  public Number() {}

  public static boolean equals$Number( XTC gold, Number n0, Number n1 ) {
    throw XEC.TODO();
  }
}
