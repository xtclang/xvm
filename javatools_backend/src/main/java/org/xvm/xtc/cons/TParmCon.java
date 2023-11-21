package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.*;

/**
   Same as a Java generic constant name: "Map<K,V>; <V> get(K key) { ... }"
 */
public class TParmCon extends FormalCon {
  private final int _reg;       // Register index
  private Parameter _parm;
  public TParmCon( CPool X ) {
    super(X);
    _reg = X.u31();
  }
  public Parameter parm() { return _parm; }
  // This guy does not have a matching Part/Component/Structure
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    MethodPart meth = (MethodPart)_par.link(repo).link(repo);
    _parm = meth._args[_reg];
    return (_part=new ParmPart(meth,this));
  }

}
