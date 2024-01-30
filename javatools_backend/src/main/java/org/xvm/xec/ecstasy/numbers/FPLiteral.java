package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xrun.Never;
import org.xvm.xec.ecstasy.Const;

/**
     Support XTC FPLiteral
*/
public class FPLiteral extends Const {
  public static final FPLiteral GOLD = new FPLiteral((Never)null);
  public FPLiteral(Never n ) {_s=null;}
  
  public final String _s;
  
  public FPLiteral(String s) { _s=s; }
  public FPLiteral(org.xvm.xec.ecstasy.text.String s) { this(s._i); }
  public FPLiteral(double x) { _s=Double.toString(x); }

  public Dec64 toDec64() { return new Dec64(_s); }
  
  @Override public String toString() { return _s; }
}
