package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVLeaf;
import org.xvm.cc_explore.tvar.TVar;

import java.util.HashSet;

// A bunch of methods, following the kids list
public class MMethodPart extends Part {
  MMethodPart( Part par, int nFlags, Const id, CondCon con, CPool X ) {
    super(par,nFlags,id,null,con,X);
  }
  
  MMethodPart( Part par, String name ) {
    super(par,name);
  }

  
  @Override public void putkid(String name, Part kid) {
    MethodPart old = (MethodPart)child(kid._name);
    if( old==null ) super.putkid(kid._name,kid);
    else {
      while( old._sibling!=null ) old = old._sibling; // Follow linked list to end
      old._sibling = (MethodPart)kid; // Append kid to tail of linked list
    }
  }

  @Override void link_innards( XEC.ModRepo repo ) {}
  // I could construct a tuple of each underlying method
  @Override TVar _setype( ) { return new TVLeaf(); }

  public MethodPart addNative() {
    assert NATIVES.contains(_name);
    MethodPart meth = new MethodPart(this,_name);
    putkid(_name,meth);
    return meth;
  }
  
  private static final HashSet<String> NATIVES = new HashSet<>() { {
      add("appendTo");
      add("equals");
      add("hashCode");
      add("set");
    } };
}
