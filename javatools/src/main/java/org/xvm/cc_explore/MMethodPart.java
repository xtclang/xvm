package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

import java.util.HashMap;

// A bunch of methods, following the kids list
public class MMethodPart extends Part {
  MMethodPart( Part par, int nFlags, IdCon id, CondCon con, CPool X ) {
    super(par,nFlags,id,null,con,X);
  }

  @Override void putkid(String name, Part kid) {
    MethodPart old = (MethodPart)_name2kid.get(kid._name);
    if( old==null ) _name2kid.put(kid._name,kid);
    else {
      while( old._sibling!=null ) old = old._sibling; // Follow linked list to end
      old._sibling = (MethodPart)kid; // Append kid to tail of linked list
    }
  }
}
