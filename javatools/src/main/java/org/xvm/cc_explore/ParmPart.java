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
  @Override public Part child(String s) {
    MethodPart meth = (MethodPart)_par;
    for( Parameter parm : meth._args )
      if( _name.equals(parm._name) ) {
        ParamTCon ptc = (ParamTCon)parm._con;
        return ((TermTCon)ptc._parms[0]).clz().child(s);
      }
    
    throw XEC.TODO();
  }  
  @Override TVar _setype( ) { return _id.setype(); }
}
