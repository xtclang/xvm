package org.xvm.xec.ecstasy;

import org.xvm.XEC;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.Orderable.Ordered;

public class Enum extends Const {
  public static final int KID = GET_KID(new Enum(null));
  public int kid() { return KID; }
  public Enum(Never n) {}
  public Enum() {}
  @Override public long hash() { return 0; }
}
