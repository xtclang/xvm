package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.xclz.AST;
import org.xvm.cc_explore.xclz.XClz;
import org.xvm.cc_explore.xclz.XClzBuilder;

public abstract class XProp extends XClz {
  
  // Normal prop impl:
  //   private Type prop;
  //   // Calls are inlined
  //   Type prop$get() { return prop; }  // Not final, can be overridden
  //   void prop$set(Type p) { prop=p; }
  //
  // Injection:
  //   private Type prop = _container.prop(); // Container name and prop name matches
  //
  // Fancier, e.g. marked LazyVar, or has non-default get/set or other pieces:
  //   private Prop$Type prop = new Prop$Type();
  //   // Calls are e.g. prop.$get() or prop.$set(p)
  public static void make_class( SB sb, PropPart pp ) {
    String pname = pp._name;
    String jtype = XClzBuilder.jtype(pp._con,false);
    ClassPart aclz = (ClassPart)pp._contribs[0]._annot.part();
    String ano = aclz._name;
    
    // Definition and init
    sb.ip("private ").p(jtype).p(" ").p(pname);
    // Special init for InjectedRef.  Other props get no init()?
    if( ano.equals("InjectedRef") )
      sb.p(" = _container.").p(pname).p("()");
    sb.p(";").nl();
    
    // private boolean prop$init;
    if( ano.equals("LazyVar") )
      sb.ip("private boolean ").p(pname).p("$init;").nl();
    
    // Type prop$get() { return prop; }  OR
    // Type prop$get() { if( !init ) { init=true; prop=calc(); } return prop; }
    sb.ip(jtype).p(" ").p(pname).p("$get() { ");
    if( ano.equals("LazyVar") )
      // Type prop$get() { if( !prop$init ) { prop$init=true; prop=prop$calc(); } return prop; };
      sb.p("if( !").p(pname).p("$init").p(") { ").p(pname).p("$init").p("=true; ").p(pname).p(" = ").p(pname).p("$calc(); } ");
    sb.p("return ").p(pname).p("; }").nl();

    // void prop$set(Type p) { prop=p; }
    sb.ip("void ").p(pname).p("$set( ").p(jtype).p(" p ) { ").p(pname).p(" = p; }").nl();

    if( pp._name2kid != null )
      for( String name : pp._name2kid.keySet() ) {
        MMethodPart mm = (MMethodPart)pp._name2kid.get(name);
        MethodPart meth = (MethodPart)mm._name2kid.get(name);
        XClzBuilder X =  new XClzBuilder(sb);
        X.jmethod(meth,pname+"$"+meth._name);
      }
    
  }
}
