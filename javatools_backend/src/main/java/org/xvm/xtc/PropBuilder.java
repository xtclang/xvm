package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.ast.AST;
import org.xvm.xtc.ast.BlockAST;
import org.xvm.xtc.ast.ReturnAST;
import org.xvm.xtc.cons.Const;

public abstract class PropBuilder {

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
  //   void prop$set(Type p) { throw new ReadOnlyException(); }
  //
  // Fancier, e.g. marked LazyVar, or has non-default get/set or other pieces:
  //   private Prop$Type prop = new Prop$Type();
  //   // Calls are e.g. prop.$get() or prop.$set(p)
  public static void make_class( ClzBuilder X, PropPart pp ) {
    // Name is unique per-class, so if embedded in XTC method make it unique
    // per java class.
    String pname = jname(pp);

    XType xtype = XType.xtype(pp._con,false);
    if( xtype instanceof XClz xclz )
      ClzBuilder.add_import(xclz);
    boolean lazy=false,inject=false;
    if( pp._contribs != null )
      for( Contrib c : pp._contribs ) {
        String ano = c._annot.part()._name;
        if( "LazyVar".equals(ano) ) lazy = true;
        if( "InjectedRef".equals(ano) ) inject = true;
      }
    boolean stat = pp.isStatic() || pp._par instanceof ModPart;
    TVarZ tfld = X._tclz.tvar(pname); // Is a type field
    boolean pub = pp._access == Const.Access.PUBLIC || (pp._access==null && X._tclz.isa(XCons.CONST));
    boolean iface = X._tclz.iface();
    SB sb = X._sb;

    boolean do_def=true, do_get=true, do_set=true;
    // Is this prop doing the *default* field definition, get, set?
    // Only if no parent is doing the defaults.
    if( isAlreadyDef(pp)  )
      do_def = do_get = do_set = false;

    // No set on injections, lazy, or final(?) init
    if( inject || pp._init != null || lazy )  do_set = false;

    // Interfaces do not get a field, but they do get getters and setters
    if( iface ) do_def=false;

    // No set property on type parameters
    if( tfld!=null ) do_set = false;

    // If overriding some methods
    if( pp._name2kid != null ) {
      for( String meth : pp._name2kid.keySet() )
        switch( meth ) {
        case "get" : do_get = true; do_def = do_set = false; break;
        case "set" : do_set = true; break;
        case "="   : do_set = false; break; // Another flavor of init flag
        case "->"  : assert lazy; do_set = false; break;
        case "calc": assert lazy && do_get; break;
        default: throw XEC.TODO();
        }
    }

    if( do_def ) {
      // Definition and init
      sb.i();                     // Not private, so child can reference
      if( stat ) sb.p("static ");
      (tfld!=null ? sb.p("$").p(tfld._name) : xtype.clz(sb)).p(" ").p(pname);
      // Special init for InjectedRef.  Other props get no init()?
      if( inject )
        sb.fmt(" = %0.XEC.CONTAINER.get().%1()",XEC.ROOT,pname);

      // Explicit init via function
      Part init;
      if( pp._name2kid != null && (init=pp._name2kid.get("="))!=null ) {
        sb.p(" = ");
        MMethodPart mm = (MMethodPart)init;
        MethodPart meth = (MethodPart)mm._name2kid.get("=");
        ClzBuilder X2 =  new ClzBuilder(X,null);
        // Method has to be a no-args function, that is executed exactly once here.
        // Inline instead.
        AST ast = X2.ast(meth);
        assert ast instanceof BlockAST blk && blk._kids.length==1 && blk._kids[0] instanceof ReturnAST && !blk.hasTemps();
        ast._kids[0]._kids[0].jcode(sb);
      }
      // Explicit init via constant
      if( pp._init != null )
        sb.p(" = ").p(XValue.val(pp._init,X));
      sb.p(";\n");

      // private boolean prop$init;
      if( lazy )
        sb.ip("private boolean ").p(pname).p("$init;\n");
    }

    // Type prop$get() { return prop; }  OR
    // Type prop$get() { if( !prop$init ) { init=true; prop=prop$calc(); } return prop; }
    if( do_get ) {
      sb.i();
      if( stat ) sb.p("static ");
      if( pub ) sb.p("public ");
      if( iface ) sb.p("abstract ");
      (tfld!=null ? sb.p("$").p(tfld._name) : xtype.clz(sb)).fmt(" %0$get()",pname);
      if( iface ) {
        sb.p(";").nl();
      } else {
        sb.p(" ");

        // Explicit get via function
        Part get;
        if( pp._name2kid != null && (get=pp._name2kid.get("get"))!=null ) {
          MMethodPart mm = (MMethodPart)get;
          MethodPart meth = (MethodPart)mm._name2kid.get("get");
          ClzBuilder X2 =  new ClzBuilder(X,X._clz);
          // Method has to be a no-args function, that is executed exactly once here.
          X2.ast(meth).jcode(sb);
        } else {
          sb.p("{ ");
          if( lazy )
            sb.fmt("if( !%0$init ) { %0$init=true; %0 = %0$calc(); } ",pname);
          sb.fmt("return %0; }\n",pname);
        }
      }
    }

    // No set property on type parameters
    if( do_set ) {

      // Explicit set via function
      Part set;
      if( pp._name2kid != null && (set=pp._name2kid.get("set"))!=null ) {
        MMethodPart mm = (MMethodPart)set;
        MethodPart meth = (MethodPart)mm._name2kid.get("set");
        ClzBuilder X2 =  new ClzBuilder(X,X._clz);
        X2.jmethod(meth,"set");

      } else {
        // void prop$set(Type p) { prop=p; }
        sb.i();
        if( stat ) sb.p("static ");
        if( pub ) sb.p("public ");
        if( iface ) sb.p("abstract ");
        sb.fmt("void %0$set( ",pname);
        xtype.clz(sb).p(" p )");
        if( iface ) {
          sb.p(";").nl();
        } else {
          sb.p(" { ");
          boolean is_const = pp._par instanceof ClassPart pclz && pclz._f == Part.Format.CONST;
          sb.p( is_const ? "throw new ReadOnlyException();" : pname + " = p;").p(" }").nl();
        }
      }
    }

    // Lazy calc
    if( lazy ) {
      String ncalc = "->";
      MMethodPart mm = (MMethodPart)pp._name2kid.get(ncalc);
      if( mm==null )
        mm = (MMethodPart)pp._name2kid.get(ncalc ="calc");
      MethodPart meth = (MethodPart)mm._name2kid.get(ncalc);
      ClzBuilder X2 =  new ClzBuilder(X,X._clz);
      X2.jmethod(meth,pname+"$calc");
    }
    sb.nl();

  }

  // Property is defined in some super class ?
  private static boolean isAlreadyDef( PropPart pp ) {
    Part p = pp._par;
    while( p!=null && !(p instanceof ClassPart) )
      p = p._par;
    while( p instanceof ClassPart cp ) {
      if( p != pp._par && p._name2kid !=null && p._name2kid.containsKey( pp._name) )
        return true;
      p = cp._super;
    }
    return false;
  }


  static String jname( PropPart pp ) {
    String name = pp._par instanceof MethodPart meth ? meth._name+"$"+pp._name : pp._name;
    // Mangle names colliding with java keywords
    return ClzBuilder.jname(name);
  }

}
