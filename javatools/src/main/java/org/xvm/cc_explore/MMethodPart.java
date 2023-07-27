package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVLeaf;
import org.xvm.cc_explore.tvar.TVar;

import java.util.IdentityHashMap;

// A bunch of methods, following the kids list
public class MMethodPart extends Part {
  MMethodPart( Part par, int nFlags, Const id, CondCon con, CPool X ) {
    super(par,nFlags,id,null,con,X);
  }
  
  MMethodPart( Part par, String name ) {
    super(par,name);
    _name2kid = new IdentityHashMap<>();
  }

  
  @Override void putkid(String name, Part kid) {
    MethodPart old = (MethodPart)child(kid._name);
    if( old==null ) _name2kid.put(kid._name,kid);
    else {
      while( old._sibling!=null ) old = old._sibling; // Follow linked list to end
      old._sibling = (MethodPart)kid; // Append kid to tail of linked list
    }
  }

  @Override void link_innards( XEC.ModRepo repo ) {}
  // I could construct a tuple of each underlying method
  @Override TVar _setype( ) { return new TVLeaf(); }  
}
