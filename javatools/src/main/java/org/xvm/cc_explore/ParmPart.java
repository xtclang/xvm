package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVar;

/**
   Package part
 */
public class ParmPart extends Part {
  TParmCon _id;
  public ParmPart( Part par, TParmCon id ) {
    super(par,0,id,null,null,null);
    _id = id;
  }  
  @Override void link_innards( XEC.ModRepo repo ) { _id.link(repo); }
  @Override TVar _setype( ) { return _id.setype(); }
}
