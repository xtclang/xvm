package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class StringCon extends Const {
  public final String _str;
  public StringCon( CPool X ) { _str = X.utf8(); }
  @Override public SB str(SB sb) { return sb.p(_str); }
  @Override public void con_link( XEC.ModRepo repo ) { }
}
