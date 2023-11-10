package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.ParamTCon;
import org.xvm.xtc.cons.TParmCon;
import org.xvm.xtc.cons.TermTCon;

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
}
