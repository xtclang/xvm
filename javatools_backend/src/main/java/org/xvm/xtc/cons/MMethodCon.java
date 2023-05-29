package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.*;

/**
  Exploring XEC Constants
 */
public class MMethodCon extends NamedCon {
  public MMethodCon( CPool X ) { super(X); }

  @Override Part _part() {
    Part p = __part();
    assert p!=null;
    return p;
  }

  private Part __part() {
    // If the default path works, take it
    Part par = _par.part();
    Part p = par.child(_name);
    if( p != null ) return p;
    // If the parent lookup fails, see if the parent is a formal/interface and
    // lookup there
    if( par instanceof MMethodPart mm )
      return mm.child(mm._name).child(_name);
    // If parent lookup fails, try again in Object
    return XEC.REPO.get("ecstasy.xtclang.org").child("Object").child(_name);
  }
}
