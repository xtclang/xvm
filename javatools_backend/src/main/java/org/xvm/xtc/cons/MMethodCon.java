package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.*;

/**
  Exploring XEC Constants
 */
public class MMethodCon extends NamedCon {
  public MMethodCon( CPool X ) { super(X); }
  @Override public Part link( XEC.ModRepo repo ) {
    MMethodPart p = (MMethodPart)super.link(repo);
    if( p!=null ) return p;
    // If the parent lookup fails, see if the parent is a formal/interface and
    // lookup there
    if( _par.part() instanceof PropPart prop ) {
      throw XEC.TODO();
    } else if( _par.part() instanceof MethodPart meth ) {
      p = (MMethodPart)meth.child(_name);
    } else if( _par.part() instanceof MMethodPart mm ) {
      MethodPart meth = (MethodPart)mm.child(mm._name);
      p = (MMethodPart)meth.child(_name);
    }
    // If parent lookup fails, try again in Object
    if( p==null )
      p = (MMethodPart)repo.get("ecstasy.xtclang.org").child("Object").child(name());
    assert p!=null;
    return (_part=p);    
  }
}
