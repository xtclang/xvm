package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.ast.AST;

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
    boolean do_def=false, do_get=false, do_set=false;
    String ano = pp._contribs==null ? null : pp._contribs[0]._annot.part()._name;
    boolean lazy = "LazyVar".equals(ano); // TODO: Need a proper flag
    boolean stat = (pp._nFlags & Part.STATIC_BIT)!=0;
    boolean tfld = (S.find(X._tclz._flds,pname)&0xFFFF) < X._tclz.nTypeParms(); // Is a type field
    boolean pub = pp._access == Const.Access.PUBLIC;
    boolean iface = X._tclz._iface;
    SB sb = X._sb;

    // Is this prop doing the *default* field definition, get, set?
    // Only if no parent is doing the defaults.
    Part p = pp._par;
    while( p!=null && !(p instanceof ClassPart) ) {
      assert p==pp._par || p._name2kid ==null || !p._name2kid.containsKey(pp._name);
      p = p._par;
    }
    boolean found=false;
    while( !found && p instanceof ClassPart cp ) {
      if( p != pp._par && p._name2kid !=null && p._name2kid.containsKey(pp._name) )
        found=true;
      p = cp._super;
    }
    // If no parent default found, we are setting all the defaults
    if( !found  )
      do_def = do_get = do_set = true;
    
    // Interfaces do not get a field, but they do get getters and setters
    if( iface ) do_def=false;
    
    // No set property on type parameters
    if( (S.find(X._tclz._flds,pname)&0xFFFF) <= X._tclz.nTypeParms() )
      do_set = false;
    
    // If overriding some methods
    if( pp._name2kid != null ) {
      for( String meth : pp._name2kid.keySet() )
        switch( meth ) {
        case "get": do_get = true; break;
        case "set": do_set = true; break;
        case "="  : do_def = true; break;
        case "->" : assert lazy; break;
        default: throw XEC.TODO();
        }
    }

    
    if( do_def ) {
      // Definition and init
      sb.i();                     // Not private, so child can reference
      if( stat ) sb.p("static ");
      (tfld ? sb.p("XTC") : xtype.clz(sb)).p(" ").p(pname);
      // Special init for InjectedRef.  Other props get no init()?
      if( "InjectedRef".equals(ano) )
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
        X2.jmethod_body_inline(meth );
      }
      // Explicit init via constant
      if( pp._init != null )
        xtype.clz(sb.p(" = new ")).fmt("(%0)",XValue.val(pp._init));
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
      (tfld ? sb.p("XTC") : xtype.clz(sb)).fmt(" %0$get()",pname);
      if( iface ) {
        sb.p(";").nl();
      } else {
        sb.p(" { ");
        if( lazy )
          sb.fmt("if( !%0$init ) { %0$init=true; %0 = %0$calc(); } ",pname);
        sb.fmt("return %0; }",pname).nl();
      }
    }

    // No set property on type parameters
    if( do_set ) {
    
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

    // Lazy calc
    if( lazy ) {
      MMethodPart mm = (MMethodPart)pp._name2kid.get("->");
      MethodPart meth = (MethodPart)mm._name2kid.get("->");
      ClzBuilder X2 =  new ClzBuilder(X,X._clz);
      X2.jmethod(meth,pname+"$calc");
    }
    sb.nl();

  }

  
  static String jname( PropPart pp ) {
    String name = pp._par instanceof MethodPart meth ? meth._name+"$"+pp._name : pp._name;
    // Mangle names colliding with java keywords
    return ClzBuilder.jname(name);
  }

}
