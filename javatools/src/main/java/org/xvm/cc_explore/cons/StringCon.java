package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.tvar.TVBase;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class StringCon extends TCon {
  public final String _str;
  public StringCon( CPool X ) { _str = X.utf8(); }
  @Override public SB str(SB sb) { return sb.p(_str); }
  @Override TVBase _setype( XEC.ModRepo repo ) { return new TVBase(this); }
}
