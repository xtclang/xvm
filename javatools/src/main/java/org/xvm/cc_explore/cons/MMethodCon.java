package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

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
      // Assume this is a single generified type, and lookup on the generic type.
      // i.e., Looking at List<Person> and now looking up against <Person>
      if( prop._con instanceof ParamTCon parm ) {
        //assert parm._parms.length==1;
        //p = (MMethodPart)parm._parms[0].link(repo).child(name(),repo);
        throw XEC.TODO();
      } else if( prop._con instanceof TermTCon ttcon ) {
        throw XEC.TODO();
      } else {
        throw XEC.TODO();
      }
    } else if( _par.part() instanceof MethodPart meth ) {
      p = (MMethodPart)meth.child(_name);
    } else if( _par.part() instanceof MMethodPart mm ) {
      MethodPart meth = (MethodPart)mm.child(mm._name);
      p = (MMethodPart)meth.child(_name);
    }
    // If parent lookup fails, try again in Object
    if( p==null )
      p = (MMethodPart)repo.get("ecstasy.xtclang.org").child("Object",repo).child(name(),repo);
    //assert p!=null;
    if( p==null )
      System.err.println("Cannot find "+name()+" in "+_par.name());
    return (_part=p);    
  }
}
