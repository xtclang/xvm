package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVStruct;

// A bunch of methods, following the kids list
public class MMethodPart extends Part {
  MMethodPart( Part par, int nFlags, IdCon id, CondCon con, CPool X ) {
    super(par,nFlags,id,null,con,X);
  }

  @Override void putkid(String name, Part kid) {
    MethodPart old = (MethodPart)child(kid._name,null);
    if( old==null ) _name2kid.put(kid._name,kid);
    else {
      while( old._sibling!=null ) old = old._sibling; // Follow linked list to end
      old._sibling = (MethodPart)kid; // Append kid to tail of linked list
    }
  }

  @Override void link_innards( XEC.ModRepo repo ) {
    // A container of 1 or more methods.  Usually 1.  If there are more than
    // one, each user has to resolve the choices individually.
    set_tvar(new TVStruct(_name,true));
  }
  
}
