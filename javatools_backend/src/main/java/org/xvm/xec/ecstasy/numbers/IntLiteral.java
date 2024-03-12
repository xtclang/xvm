package org.xvm.xec.ecstasy.numbers;

import org.xvm.xec.ecstasy.Const;
import org.xvm.xrun.Never;

/**
     Support XTC IntLiteral
*/
public class IntLiteral extends Const {
  public static final IntLiteral GOLD = new IntLiteral((Never)null);
  public IntLiteral(Never n ) {_s=null;}
  
  public final String _s;
  
  public IntLiteral(String s) { _s=s; }
  public IntLiteral(org.xvm.xec.ecstasy.text.String s) { this(s._i); }
  public IntLiteral(long x) { _s=Long.toString(x); }

  public static IntLiteral construct(String s) { return new IntLiteral(s); }
  public long toInt64() { return Long.valueOf(_s); }
  public long toInt  () { return Long.valueOf(_s); }
  @Override public String toString() { return _s; }
}
