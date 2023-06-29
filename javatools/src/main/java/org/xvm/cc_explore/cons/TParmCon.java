package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVar;

/**
   Same as a Java generic constant name: "Map<K,V>; <V> get(K key) { ... }"
 */
public class TParmCon extends FormalCon {
  private final int _reg;       // Register index
  public TParmCon( CPool X ) {
    super(X);
    _reg = X.u31();
  }
  // This guy does not have a matching Part/Component/Structure
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    MethodPart meth = (MethodPart)_par.link(repo).link(repo);
    Parameter parm = meth._args[_reg];
    _tvar = parm.tvar();
    return (_part=new ParmPart(meth,this));
  }
}
