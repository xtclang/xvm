package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.CondCon;
import org.xvm.xtc.cons.Const;

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

  public MethodPart addNative() {
    assert NATIVES.contains(_name);
    MethodPart meth = new MethodPart(this,_name);
    putkid(_name,meth);
    return meth;
  }
  
  private static final HashSet<String> NATIVES = new HashSet<>() { {
      add("appendTo");
      add("compare");
      add("equals");
      add("get");
      add("hashCode");
      add("set");
    } };
}
