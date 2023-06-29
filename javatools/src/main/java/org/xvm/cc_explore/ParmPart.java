package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

/**
   Package part
 */
public class ParmPart extends Part {
  public ParmPart( Part par, TParmCon id ) {
    super(par,0,id,null,null,null);
    set_tvar(id.tvar());
  }  
  @Override void link_innards( XEC.ModRepo repo ) { }
}
