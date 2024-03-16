package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.ParamTCon;
import org.xvm.xtc.cons.TParmCon;
import org.xvm.xtc.cons.TermTCon;

/**
   Package part
 */
public class ParmPart extends Part {
  final int _idx;
  public ParmPart( MethodPart par, int idx ) {
    super(par,0,null,par._args[idx]._name,null,null);
    _idx = idx;
  }
  @Override void link_innards( XEC.ModRepo repo ) {}
  Parameter parm() { return ((MethodPart)_par)._args[_idx]; }
}
