package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVStruct;
import org.xvm.cc_explore.tvar.TVar;

// A bunch of methods, following the kids list
public class MMethodPart extends Part {
  MMethodPart( Part par, int nFlags, Const id, CondCon con, CPool X ) {
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

  @Override void link_innards( XEC.ModRepo repo ) {}
  @Override TVar _setype( ) {
    throw XEC.TODO();
  }
  
}
