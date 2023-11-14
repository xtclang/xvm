package org.xvm.xrun;

import org.xvm.xtc.*;
import org.xvm.util.SB;
import org.xvm.xec.XClz;

public abstract class XProp extends XClz {
  
  // Normal prop impl:
  //   private Type prop;
  //   Type prop$get() { return prop; }  // Not final, can be overridden
  //   void prop$set(Type p) { prop=p; }
  //
  // Injection gets initialized from container:
  //   private Type prop = _container.prop(); // Container name and prop name matches
  //
  // LazyVar:
  //   private boolean prop$init;
  //   Type prop$get() { if( !prop$init ) { prop$init=true; prop=prop$calc(); } return prop; }
  //   Type prop$calc() { ... }
  //
  // Const:
  //   void prop$set(Type p) { throw new ReadOnlyX(); }
  //
  // Fancier, e.g. marked LazyVar, or has non-default get/set or other pieces:
  //   private Prop$Type prop = new Prop$Type();
  //   // Calls are e.g. prop.$get() or prop.$set(p)
  public static void make_class( ClzBuilder X, PropPart pp ) {
    // Name is unique per-class, so if embedded in XTC method make it unique
    // per java class.
    String pname = jname(pp);

    XType xtype = XType.xtype(pp._con,false);
    String ano = pp._contribs==null ? null : pp._contribs[0]._annot.part()._name;
    boolean lazy = "LazyVar".equals(ano);
    boolean stat = (pp._nFlags & Part.STATIC_BIT)!=0;
    
    // Definition and init
    SB sb = X._sb;
    sb.ip("private ");
    if( stat ) sb.p("static ");
    xtype.clz(sb).p(" ").p(pname);
    // Special init for InjectedRef.  Other props get no init()?
    if( "InjectedRef".equals(ano) )
      sb.p(" = _container.").p(pname).p("()");

    // Explicit init
    Part init;
    if( pp._name2kid != null && (init=pp._name2kid.get("="))!=null ) {
      sb.p(" = ");
      MMethodPart mm = (MMethodPart)init;
      MethodPart meth = (MethodPart)mm._name2kid.get("=");
      ClzBuilder X2 =  new ClzBuilder(X,null);
      // Method has to be a no-args function, that is executed exactly once here.
      // Inline instead.
      X2.jmethod_body_inline(meth );
    }
    sb.p(";").nl();
    
    // private boolean prop$init;
    if( lazy )
      sb.ip("private boolean ").p(pname).p("$init;").nl();
    
    // Type prop$get() { return prop; }  OR
    // Type prop$get() { if( !prop$init ) { init=true; prop=prop$calc(); } return prop; }
    sb.i();
    if( stat ) sb.p("static ");
    xtype.clz(sb).p(" ").p(pname).p("$get() { ");
    if( lazy )
      sb.p("if( !").p(pname).p("$init").p(") { ").p(pname).p("$init").p("=true; ").p(pname).p(" = ").p(pname).p("$calc(); } ");
    sb.p("return ").p(pname).p("; }").nl();

    // void prop$set(Type p) { prop=p; }
    sb.i();
    if( stat ) sb.p("static ");
    sb.p("void ").p(pname).p("$set( ");
    xtype.clz(sb).p(" p ) { ");
    boolean is_const = pp._par instanceof ClassPart pclz && pclz._f == Part.Format.CONST;
    sb.p( is_const ? "throw new ReadOnlyX();" : pname + " = p;").p(" }").nl();

    // Lazy calc
    if( lazy ) {
      MMethodPart mm = (MMethodPart)pp._name2kid.get("->");
      MethodPart meth = (MethodPart)mm._name2kid.get("->");
      ClzBuilder X2 =  new ClzBuilder(X,null);
      X2.jmethod(meth,pname+"$calc");
    }

  }

  public static String jname( PropPart pp ) {
    return pp._par instanceof MethodPart meth ? meth._name+"$"+pp._name : pp._name;
  }

}
