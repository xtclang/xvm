package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;

import java.util.HashSet;

// A bunch of methods, following the kids list
public class MMethodPart extends Part {
  MMethodPart( Part par, int nFlags, Const id, CondCon con, CPool X ) {
    super(par,nFlags,id,null,con,X);
  }
  
  MMethodPart( Part par, String name ) { super(par,name); }
  
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
    ClassCon tcon = (ClassCon)_par._id;
    MethodPart meth = switch( _name ) {
    case "equals"   -> build(XCons.BOOL,"gold",tcon,"lhs",tcon,"rhs",tcon);
    case "hashCode" -> build(XCons.LONG,"gold",tcon);
    case "compare"  -> build(XCons.ORDERED,"lhs",tcon,"rhs",tcon);
    case "appendTo" -> build(XCons.APPENDERCHAR,new Parameter[]{new Parameter("buf",XCons.APPENDERCHAR)});
    default -> throw XEC.TODO();
    };
    putkid(_name,meth);
    return meth;
  }

  private MethodPart build(XType xret, Object... os) {
    Parameter[] args = new Parameter[os.length>>1];
    for( int i=0; i<args.length; i++ )
      args[i] = new Parameter((String)os[i<<1],(TCon)os[(i<<1)+1]);
    return build(xret,args);
  }
  private MethodPart build(XType xret, Parameter[] args) {
    Parameter[] rets = new Parameter[]{new Parameter("#ret",xret)};
    return new MethodPart(this,_name,args,rets);
  }
  
  private static final HashSet<String> NATIVES = new HashSet<>() { {
      add("add");
      add("addAll");
      add("appendTo");
      add("compare");
      add("construct");
      add("equals");
      add("get");
      add("hashCode");
      add("set");
    } };
}
