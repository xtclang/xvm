package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;
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
      yield ASB.p(ic._x);
    }

    // Character constant
    case CharCon cc -> 
      ASB.p('\'').p((char)cc._ch).p('\'');

    // String constants
    case StringCon sc ->
      ASB.p('"').escape(sc._str).p('"');
       
    // Literal constants
    case LitCon lit -> 
      ASB.p(lit._str);
    
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
        if( clz!= ClzBuilder.CCLZ )
          name = clz._name+"."+name;
      }
      yield ASB.p(name);
    }

    // Property constant.  Just the base name, and depending on usage
    // will be either console$get() or console$set(value).
    case PropCon prop ->
      ASB.p(prop._name);

    // A class Type as a value
    case ParamTCon ptc ->
      ((XType.ClzClz)XType.xtype(ptc,false))._clz.clz(ASB).p(".GOLD");
    
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
        yield ASB.p( ClzBuilder.java_class_name(mod._name));
      if( con0.part() instanceof PropPart prop )
        yield ASB.p(PropBuilder.jname(prop)).p("$get()");
      throw XEC.TODO();
    }

    case RangeCon rcon -> {
      String ext = rcon._xlo
        ? (rcon._xhi ? "EE" : "EI")
        : (rcon._xhi ? "IE" : "II");
      ClzBuilder.XTC_IMPORTS.add("ecstasy.Range"+ext);
      ASB.p("new Range").p(ext).p("(");
      _val(rcon._lo).p(",");
      _val(rcon._hi).p(")");
      yield ASB;
    }
    
    // Array constants
    case AryCon ac -> {
      assert ac.type() instanceof ImmutTCon; // Immutable array goes to static
      XType.Ary ary = (XType.Ary)XType.xtype(ac.type(),false);
      // new Ary<String>( "abc", "def");
      // new Arylong( 0, 1, 2 );
      // new Ary<Arylong>( new Arylong(0) )
      ary.p(ASB.p("new "));
      ASB.p("(  ");
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
        yield ASB.p("_container.console()");
      throw XEC.TODO();      
    }

    case Dec64Con dcon -> 
      ASB.p("new Dec64(").p(dcon._dec.toString()).p(")");
    
    default -> throw XEC.TODO();
    };
  }
}
