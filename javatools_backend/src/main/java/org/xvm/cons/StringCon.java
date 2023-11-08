package org.xvm.cons;

import org.xvm.CPool;
import org.xvm.XEC;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class StringCon extends TCon {
  public final String _str;
  public StringCon( CPool X ) { _str = X.utf8(); }
  @Override public SB str(SB sb) { return sb.p(_str); }
}
