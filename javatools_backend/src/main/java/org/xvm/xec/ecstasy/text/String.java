package org.xvm.xec.ecstasy.text;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xrun.Never;

public class String extends Const {
  public static final String GOLD = new String((Never)null);
  public String(Never n) { _s=null; }
  public final java.lang.String _s;
  public String(java.lang.String s) { _s = s; }
  public int length() { return _s.length(); }
  public char charAt(int x) { return _s.charAt(x); }
  public static <E extends String> boolean equals$String( XTC gold, E ord0, E ord1 ) { return ord0==ord1; }
}
