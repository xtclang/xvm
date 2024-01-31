package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;
import org.xvm.util.S;
import org.xvm.util.SB;

// Concrete java values from XTC values.
public abstract class XValue {
  // Produce a java value from a TCon
  private static final SB ASB = new SB();
  static public String val( Const tc ) {
    assert ASB.len()==0;
    // Caller is a switch, will encode special
    if( tc instanceof MatchAnyCon ) return null;
    _val(tc);
    String rez = ASB.toString();
    ASB.clear();
    return rez;
  }
  private static SB _val( Const tc ) {
    return switch( tc ) {
      // Integer constants in XTC are Java Longs
    case IntCon ic -> {
      if( ic._big != null ) throw XEC.TODO();
      ASB.p(ic._x);
      if( ic._x >= Integer.MAX_VALUE || ic._x <= Integer.MIN_VALUE )  ASB.p("L");
      yield ASB;
    }
    case Flt64Con fc ->
      ASB.p(fc._flt);

    // Character constant
    case CharCon cc -> 
      ASB.p('\'').p((char)cc._x).p('\'');
    case ByteCon bc ->
      ASB.p(bc._x);

    // String constants
    case StringCon sc ->
      ASB.quote(sc._str);
       
    // Literal constants
    case LitCon lit -> 
      ASB.quote(lit._str);
    
    // Method constants
    case MethodCon mcon -> {
      MethodPart meth = (MethodPart)mcon.part();
      // Lambda names "->" are inlined as Java lambdas
      String name = meth._name;
      if( meth._par._par instanceof MethodPart pmeth ) {
        if( !name.equals("->") )
          name = pmeth._name+"$"+meth._name;
      } else {
        // Put qualified name if not in local namespace
        ClassPart clz = (ClassPart)meth._par._par;
        if( clz!= ClzBuilder.CMOD )
          name = ClzBuilder.add_import(XClz.make(clz)).clz_bare()+"."+name;
      }
      yield ASB.p(name);
    }

    // Property constant.  Just the base name, and depending on usage
    // will be either console$get() or console$set(value).
    case PropCon prop -> {
      Part par = prop.part()._par;
      if( par != ClzBuilder.CCLZ && ((prop.part()._nFlags & Part.STATIC_BIT) != 0) ) {
        if( par instanceof ClassPart clz )
          ASB.p(ClzBuilder.add_import(clz).clz_bare()).p('.');
        else if( par instanceof MethodPart meth ) {
          ASB.p(meth._name).p('$');
        } else throw XEC.TODO();
      }
      yield ASB.p(prop._name);
    }

    // A class Type as a value
    case ParamTCon ptc -> {
      XType xt = XType.xtype(ptc,false);
      // TParmCon is a parameterized type, just use generic
      if( ptc._parms[0] instanceof TermTCon ttc && ttc.id() instanceof TParmCon tpc )
        yield ASB.p(tpc._name);
      if( ptc._parms[0] instanceof ParamTCon parm && parm.part()._name.equals("Type") ) // Dynamic formal type?
        // Trim off leading $, get the parameter name, not the generic name
        yield ASB.p(xt.toString().substring(1));
      if( xt instanceof XClz clz )
        yield ClzBuilder.add_import(clz).clz(ASB).p(".GOLD");
      // TODO: ASSERT XClz from PTC, get the gold instance from the one type parameter?
      // Assert ntypeparms ==1, get field#0 as the type, get gold from that?
      yield ASB.p("org.xvm.xec.ecstasy.reflect.Type.GOLD");
      //throw XEC.TODO();
    }
    
    // Enums
    case EnumCon econ -> {
      ClassPart clz = (ClassPart)econ.part();
      String sup_clz = clz._super._name;
      // Just use Java null
      if( sup_clz.equals("Nullable") )
        yield ASB.p("null");
      // XTC Booleans rewrite as Java booleans
      if( sup_clz.equals("Boolean") ) 
        yield ASB.p(clz._name.equals("False") ? "false" : "true");
      // Use the enum name directly
      yield ASB.p(sup_clz).p(".").p(clz._name);
    }

    // Singleton class constants (that are not enums)
    case SingleCon con0 -> {
      if( con0.part() instanceof ModPart mod )
        yield ASB.p( S.java_class_name(mod._name));
      if( con0.part() instanceof PropPart prop )
        yield ASB.p(PropBuilder.jname(prop)).p("$get()");
      throw XEC.TODO();
    }

    case RangeCon rcon -> {
      String ext = rcon._xlo
        ? (rcon._xhi ? "EE" : "EI")
        : (rcon._xhi ? "IE" : "II");
      ClzBuilder.IMPORTS.add(XEC.XCLZ+".ecstasy.Range"+ext);
      ASB.p("new Range").p(ext).p("(");
      _val(rcon._lo).p(",");
      _val(rcon._hi).p(")");
      yield ASB;
    }
    
    // Array constants
    case AryCon ac -> {
      assert ac.type() instanceof ImmutTCon; // Immutable array goes to static
      XType ary = XType.xtype(ac.type(),false);
      // Cannot make a zero-length instance of ARRAY, since its abstract - but
      // a zero-length version is meaningful.
      if( ary==XCons.ARRAY ) {
        ClzBuilder.IMPORTS.add(XEC.XCLZ+".ecstasy.collections.AryXTC");
        yield ASB.p("AryXTC.EMPTY"); // Untyped array creation; cannot hold elements
      }
      
      // new Ary<String>( "abc", "def");
      // new Arylong( 0, 1, 2 );
      // new AryXTC<Arylong>( new Arylong(0) )
      ary.clz(ASB.p("new ")).p("(  ");
      if( ary.generic_ary() )
        ary.e().clz(ASB).p(".GOLD, ");
      else if( !ary.isTuple() )
        ASB.p(".1, ");
      if( ac.cons()!=null )
        for( Const con : ac.cons() )
          _val( con ).p(", ");
      yield ASB.unchar(2).p(")");
    }

    // Map constants
    case MapCon mc -> {
      XType type = XType.xtype(mc._t,false);
      type.str(ASB.p("new ")).p("() {{ ");
      for( int i=0; i<mc._keys.length; i++ ) {
        ASB.p("put(");
        _val( mc._keys[i] ).p(",");
        _val( mc._vals[i] ).p("); ");
      }
      ASB.p("}} ");
      yield ASB;
    }

    // Special TermTCon
    case TermTCon ttc -> {
      ClassPart clz = ttc.clz();
      if( clz._name.equals("Console") && clz._path._str.equals("ecstasy/io/Console.x") )
        yield ASB.p(XEC.ROOT).p(".XEC.CONTAINER.get()").p(".console()");
      if( clz._name.equals("Timer") && clz._path._str.equals("ecstasy/temporal/Timer.x") )
        yield ASB.p(XEC.ROOT).p(".XEC.CONTAINER.get()").p(".timer()");
      throw XEC.TODO();      
    }

    case Dec64Con dcon -> 
      ASB.p("new Dec64(\"").p(dcon.asStr()).p("\")");
    
    case Dec128Con dcon -> 
      ASB.p("new Dec128(\"").p(dcon.asStr()).p("\")");

    case ClassCon ccon -> {
      ClzBuilder.add_import( ccon.clz() );
      XType x = XType.xtype(ccon,false);
      yield x.clz(ASB.p("new ")).p("()");
    }

    case AnnotTCon acon ->
      switch( acon.clz()._name ) {
        // Gets a special "CONTAINER.inject" call
      case "InjectedRef" -> _val(acon.con().is_generic());
      // Wraps the type as "Future<whatever>".
      case "FutureVar" -> XType.xtype(acon,true).clz(ASB.p("new ")).p("()");

      default -> throw XEC.TODO();
      };

    
    default -> throw XEC.TODO();
    };
  }
}
