package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.*;
import org.xvm.cc_explore.xrun.*;

import java.lang.Character;
import java.util.HashMap;
import javax.lang.model.SourceVersion;
import java.lang.reflect.Constructor;


// XTC Module is roughly a java package, but I don't want to deal with directories
// So a module becomes "JModXNAME extends XClz" class.
// Then a XTC class file (can be inside a single-class module file)
// extends this: "JClzTest extends JModTest".




// Some kind of base class for a Java class that implements an XTC Module
public class XClzBuilder {

  
  public final ModPart _mod;
  public final SB _sb;
  // Fields for emitting a Method Code
  public MethodPart _meth;      // Method whose code is being parsed
  private CPool _pool;          // Parser for code buffer
  final HashMap<String,String> _names; // Java namification
  final NonBlockingHashMapLong<String> _locals, _ltypes; // Virtual register numbers to java names
  int _nlocals;                 // Number of locals defined; popped when lexical scopes pop

  // A collection of extra class source strings
  private static final HashMap<String,String> XCLASSES = new HashMap<>();

  public XClzBuilder( ModPart mod ) { this(mod,new SB()); }
  public XClzBuilder( SB sb ) { this(null,sb); }
  private XClzBuilder( ModPart mod, SB sb ) {
    _mod = mod;
    _sb = sb;
    _names = new HashMap<>();
    _locals = new NonBlockingHashMapLong<>();
    _ltypes = new NonBlockingHashMapLong<>();
  }

  // Convert a local name to the local index
  long name2idx( String name ) {
    for( long l : _locals.keySetLong() )
      if( name.equals(_locals.get(l)) )
        return l;
    return -1;
  }

  
  // Let's start by assuming if we're here, we're inside the top-level
  // ecstasy package - otherwise we're nested instead the mirror for the
  // containing package.
  public XRunClz xclz() {
    assert _mod.child("ecstasy") instanceof PackagePart;
    assert XCLASSES.isEmpty();

    // The Java class will extend XClz.
    // The Java class name will be the mangled module class-name.
    String java_class_name = java_class_name(_mod._name);
    jclass(java_class_name);
    // Output extra helper classes, if any
    for( String source : XCLASSES.values() )
      _sb.nl().p(source);    
    XCLASSES.clear();
    
    System.out.println(_sb);
    
    try {
      Class<XRunClz> clz = XClzCompiler.compile(XEC.XCLZ+"."+java_class_name, _sb.toString());
      Constructor<XRunClz> xcon = clz.getConstructor(Container.class);
      return xcon.newInstance(new NativeContainer());
    } catch( Exception ie ) {
      throw XEC.TODO();
    }
  }

  
  // Fill in the body of the matching java class
  private void jclass( String java_class_name ) {
    _sb.p("// Auto Generated by XEC from ").p(_mod._dir._str).p(_mod._path._str).nl().nl();
    _sb.p("package ").p(XEC.XCLZ).p(";").nl().nl();
    _sb.p("import ").p(XEC.ROOT).p(".xrun.*;").nl();
    _sb.p("import static ").p(XEC.ROOT).p(".xrun.XRuntime.$t;").nl();
    _sb.nl();
    jclass_body( java_class_name );
  }

  private void jclass_body( String java_class_name ) {
    boolean top_level = _mod!=null;
    _sb.p("public ");
    if( !top_level ) _sb.p("static ");
    _sb.p("class ").p(java_class_name).p(" extends XRunClz {").nl().ii();

    // Required constructor to inject the container
    if( top_level )
      _sb.ip("public ").p(java_class_name).p("( Container container ) { super(container); }").nl();
    
    // Look for a module init.  This will become the Java <clinit>
    MMethodPart mm = (MMethodPart)_mod.child("construct");
    MethodPart construct = (MethodPart)mm.child(mm._name);
    if( construct != null ) {
      assert _locals.isEmpty() && _nlocals==0; // No locals mapped yet
      assert construct._sibling==null;
      // Skip common empty constructor
      if( !construct.is_empty_function() ) {
        _sb.nl();
        _sb.ip("static {").nl();
        ast(construct).jcode(_sb );
        _sb.ip("}").nl().nl();
      }
    }

    // Output Java methods for all Module methods
    // TODO: Classes in a Module?
    for( Part part : _mod._name2kid.values() ) {
      if( part instanceof MMethodPart mmp ) {
        if( mmp._name.equals("construct") ) continue; // Already handled module constructor
        _sb.nl();
        MethodPart meth = (MethodPart)mmp.child(mmp._name);
        jmethod(meth,meth._name);
      } else if( part instanceof PackagePart ) {
        // Self module is OK
      } else if( part instanceof PropPart pp ) {
        XProp.make_class(_sb,pp); // <clinit> for a static global property
      } else if( part instanceof ClassPart clz ) {
        // Nested class.  Becomes a java static inner class
        XClzBuilder X = new XClzBuilder(_sb);
        X.jclass_body(java_class_name(clz._name));
        
      } else {
        throw XEC.TODO();
      }
    }

    // End the class body
    _sb.di().p("}").nl();    
  }

  // Emit a Java string for this MethodPart.
  // Already _sb has the indent set.
  public void jmethod( MethodPart m, String mname ) {
    assert _locals.isEmpty() && _nlocals==0; // No locals mapped yet
    _sb.ip("public ");
    // Return type
    if( m._rets==null ) _sb.p("void ");
    else if( m._rets.length == 1 ) _sb.p(jtype(m._rets[0]._con,false)).p(' ');
    else throw XEC.TODO(); // Multi-returns will need much help
    // Argument list
    _sb.p(mname).p("( ");
    if( m._args!=null ) {
      for( int i = 0; i < m._args.length; i++ ) {
        Parameter p = m._args[i];
        String type = jtype(p._con,false);
        _sb.p(type).p(' ').p(p._name).p(", ");
        define(p._name,type);
      }
      _sb.unchar(2);
    }
    _sb.p(" ) ");
    // Body    
    ast(m).jcode(_sb);
    _sb.nl();

    // Popped back to the original args
    assert (m._args==null ? 0 : m._args.length) == _nlocals;
    pop_locals(0);

    if( m._name2kid != null )
      for( Part part : m._name2kid.values() ) {
        if( part instanceof PropPart pp )
          XProp.make_class(_sb,pp);
        else if( part instanceof MMethodPart mmp ) {
          // Lambda expressions have been inlined
          if( mmp._name.equals("->") ) ;
          else {
            // Recursively dump name
            MethodPart meth = (MethodPart)mmp.child(mmp._name);
            String name = mname+"$"+mmp._name;
            jmethod(meth,name);
          }
        } 
        else throw XEC.TODO();
      }
  }

  public AST ast( MethodPart m ) {
    // Build the AST from bytes
    _meth = m;
    _pool = new CPool(m._ast,m._cons); // Setup the constant pool parser
    AST ast = AST.parse(this);
    // Do any trivial restructuring
    return ast.do_rewrite();
  }

  
  int u8 () { return _pool.u8(); }  // Packed read
  int u31() { return _pool.u31(); } // Packed read
  long pack64() { return _pool.pack64(); }
  // Read an array of method constants
  Const[] consts() { return _pool.consts(); }
  // Read a single method constant, advancing parse pointer
  Const con() { return con(u31()); }
  // Read a single method constant
  Const con(int i) { return _meth._cons[i]; }

  // Read an array of AST kid terminals
  AST[] kids() { return _kids(u31(),0); }
  AST[] kids( int n ) { return _kids(n,0); }
  // Read an array of AST kid terminals, with a given bias (skipped elements are null).
  AST[] kids_bias( int b ) { return _kids(u31(),b); }
  
  private AST[] _kids( int n, int bias ) {
    if( n+bias==0 ) return null;
    AST[] kids = new AST[n+bias];
    for( int i=0; i<n; i++ )
      kids[i+bias] = AST.ast_term(this);
    return kids;
  }


  // --------------------------------------------------------------------------

  void define( String name, String type ) {
    // Track active locals
    _locals.put(_nlocals  ,name);
    _ltypes.put(_nlocals++,type);
  }
    // Pop locals at end of scope
  void pop_locals(int n) {
    while( n < _nlocals ) {
      _ltypes.remove(  _nlocals);
      _locals.remove(--_nlocals);
    }
  }

  static String java_class_name( String xname ) {
    return "J"+xname;
  }

  // After the basic mangle, dups are suffixed 1,2,3...
  String jname( String xname ) {
    String s = _mangle(xname);
    boolean unique = true;
    int max = 0;
    for( String old : _locals.values() ) {
      if( s.equals(old) ) unique = false;
      else if( old.startsWith(s) ) {
        int last = old.length();
        while( Character.isDigit(old.charAt(last-1)) ) last--;
        if( last == old.length() ) continue; // No trailing digits to confuse
        int num = Integer.parseInt(old.substring(last));
        max = Math.max(max,num);
      }
    }

    return unique ? s : s+(max+1);
  }

  // If the name is a valid java id, keep it.
  // If the name starts "loop#", use "i"
  // keep the valid prefix, and add $.
  private static String _mangle( String name ) {
    // Valid java name, just keep it
    // Valid except a keyword, add "$"
    if( SourceVersion.isIdentifier(name) )
      return SourceVersion.isKeyword(name) ? name+"$" : name;
    // Starts with "loop#", assume it is a generated loop variable for iterators
    if( name.startsWith("loop#") ) return "i";
    // Keep valid prefix and add "$"
    throw XEC.TODO();
  }


  // A set of common XTC classes, and their Java replacements.
  // These are NOT parameterized.
  static final HashMap<String,String> XJMAP = new HashMap<>() {{
      put("Boolean+ecstasy/Boolean.x","boolean");
      put("Char+ecstasy/text/Char.x","char");
      put("Console+ecstasy/io/Console.x","Console");
      put("Int64+ecstasy/numbers/Int64.x","long");
      put("IntLiteral+ecstasy/numbers/IntLiteral.x","long");
      put("Object+ecstasy/Object.x","Object");
      put("String+ecstasy/text/String.x","String");
      put("StringBuffer+ecstasy/text/StringBuffer.x","StringBuffer");
    }};

  // Convert a Java primitive to the Java object version.
  static final HashMap<String,String> XBOX = new HashMap<>() {{
      put("char","Character");
      put("int","Integer");
      put("long","Long");
    }};
  public static String box(String s) {
    String box = XBOX.get(s);
    return box==null ? s : box;
  }

  
  // Produce a java type from a TermTCon
  public static String jtype( Const tc, boolean boxed ) {
    if( tc instanceof TermTCon ttc ) {
      ClassPart clz = (ClassPart)ttc.part();
      String key = clz._name + "+" + clz._path._str;
      String val = XJMAP.get(key);
      if( val!=null )
        return boxed ? box(val) : val;
      throw XEC.TODO();
    }
    
    if( tc instanceof ParamTCon ptc ) {
      String telem = ptc._parms==null ? null : jtype(ptc._parms[0],true);
      ClassPart clz = ((ClzCon)ptc._con).clz();
      
      // These XTC classes are all intercepted and directly implemented in Java
      if( clz._name.equals("Array") && clz._path._str.equals("ecstasy/collections/Array.x") )
        return telem.equals("Long")
          ? "AryI64"            // Java ArrayList specialized to int64
          : "Ary<"+telem+">";   // Shortcut class

      // All the long-based ranges, intervals and interators are just Ranges now.
      if( clz._name.equals("Range") && clz._path._str.equals("ecstasy/Range.x") ||
          clz._name.equals("Interval") && clz._path._str.equals("ecstasy/Interval.x") ) {
        if( telem.equals("Long") ) return "Range"; // Shortcut class
        else throw XEC.TODO();
      }
      if( clz._name.equals("Iterator") && clz._path._str.equals("ecstasy/Iterator.x") ) {
        if( telem.equals("Long") ) return "XIter64"; // Shortcut class
        else throw XEC.TODO();
      }
    
      if( clz._name.equals("List") && clz._path._str.equals("ecstasy/collections/List.x") )
        return "Ary<"+telem+">"; // Shortcut class
      
      if( clz._name.equals("Tuple") && clz._path._str.equals("ecstasy/collections/Tuple.x") )
        return Tuple.make_class(XCLASSES, ptc._parms);

      if( clz._name.equals("Map") && clz._path._str.equals("ecstasy/collections/Map.x") )
        return XMap.make_class(XCLASSES, ptc._parms);
      
      // Attempt to use the Java class name
      if( clz._name.equals("Type") && clz._path._str.equals("ecstasy/reflect/Type.x") )
        return telem + ".class";
          
      if( clz._name.equals("Function") && clz._path._str.equals("ecstasy/reflect/Function.x") )
        // TODO: Gonna need more type info that this
        return "XFunc";
      
      throw XEC.TODO();
    }
    if( tc instanceof ImmutTCon itc ) 
      return jtype(itc.icon(),boxed); // Ignore immutable for now

    // Generalized union types gonna wait awhile.
    // Right now, allow null unions only
    if( tc instanceof UnionTCon utc ) {
      if( ((ClzCon)utc._con1).clz()._name.equals("Nullable") )
        return jtype(utc._con2,true);
      throw XEC.TODO();
    }

    
    throw XEC.TODO();
  }  

  // Produce a java value from a TCon
  private static final SB ASB = new SB();
  static public String value_tcon( Const tc ) {
    assert ASB.len()==0;
    // Caller is a switch, will encode special
    if( tc instanceof MatchAnyCon ) return null;
    _value_tcon(tc);
    String rez = ASB.toString();
    ASB.clear();
    return rez;
  }
  private static SB _value_tcon( Const tc ) {
    // Integer constants in XTC are Java Longs
    if( tc instanceof IntCon ic ) {
      if( ic._big != null ) throw XEC.TODO();
      return ASB.p(ic._x).p('L');
    }

    // Character constant
    if( tc instanceof CharCon cc )
      return ASB.p('\'').p((char)cc._ch).p('\'');

    // String constants
    if( tc instanceof StringCon sc )
      return ASB.p('"').escape(sc._str).p('"');
       
    // Literal constants
    if( tc instanceof LitCon lit )
      return ASB.p(lit._str);
    
    // Method constants
    if( tc instanceof MethodCon mcon )  {
      MethodPart meth = (MethodPart)mcon.part();
      // TODO: Assumes the method is in the local Java namespace
      String name = meth._name;
      if( meth._par._par instanceof MethodPart pmeth )
        name = pmeth._name+"$"+meth._name;
      return ASB.p(name);
    }

    // Property constant.  Just the base name, and depending on usage
    // will be either console$get() or console$set(value).
    if( tc instanceof PropCon prop )
      return ASB.p(prop._name).p("$get()");

    // A class Type as a value
    if( tc instanceof ParamTCon ptc )
      return ASB.p(jtype(ptc,false));
    
    // Enums
    if( tc instanceof EnumCon econ ) {
      ClassPart clz = (ClassPart)econ.part();
      String sup_clz = clz._super._name;
      // Just use Java null
      if( sup_clz.equals("Nullable") )
        return ASB.p("null");
      // XTC Booleans rewrite as Java booleans
      if( sup_clz.equals("Boolean") ) 
        return ASB.p(clz._name.equals("False") ? "false" : "true");
      // Intercept Tuple enums
      if( sup_clz.equals("Mutability") ) {
        if( clz._super._par._name.equals("Tuple") ) {
          return ASB.p("Tuple.Mutability.").p(clz._name);          
        } else
          throw XEC.TODO();
      }
      // Use the enum name directly
      return ASB.p(sup_clz).p(".").p(clz._name);
    }

    // Singleton class constants (that are not enums)
    if( tc instanceof SingleCon con0 )
      return ASB.p(java_class_name(((ModPart)con0.part())._name));

    if( tc instanceof RangeCon rcon ) {
      String ext = rcon._xlo
        ? (rcon._xhi ? "EE" : "EI")
        : (rcon._xhi ? "IE" : "II");
      ASB.p("new Range").p(ext).p("(");
      _value_tcon(rcon._lo).p(",");
      _value_tcon(rcon._hi).p(")");
      return ASB;
    }
    
    // Array constants
    if( tc instanceof AryCon ac ) {
      assert ac.type() instanceof ImmutTCon; // Immutable array goes to static
      String type = jtype(ac.type(),false);
      int lastx = type.length()-1;
      char ch = type.charAt(lastx);
      
      if( ch!=']' ) { // e.g. AryI64 or Ary<AryI64>
        // Generic Ary flavors.
        int genx = type.indexOf('<');
        String  genclz = ch=='>' ? type.substring(genx+1,lastx) : null;
        String baseclz = ch=='>' ? type.substring(0,genx) : type;        
        ASB.p("new ").p(baseclz);

        if( baseclz.contains("Tuple") ) {
          ASB.p("(");
          if( ac.cons()!=null ) {
            for( Const con : ac.cons() )
              _value_tcon( con ).p(", ");
            ASB.unchar(2);
          }
          ASB.p(")");
        } else {
          
          assert baseclz.startsWith("Ary");          
          ASB.p("(");
          if( genclz != null ) ASB.p(genclz).p(".class");
          ASB.p(")");
        
          if( ac.cons()!=null )
            for( Const con : ac.cons() ) {
              ASB.p(".add(");
              _value_tcon( con ).p(")");
            }
        }

      } else {
        // Standard arrays, e.g. long[]
        ASB.p("new ").p(type).p("[]");
        if( ac.cons()!=null ) {
          ASB.p("{ ");
          for( Const con : ac.cons() )
            _value_tcon( con ).p(", ");
          ASB.p("}");
        }
      }
      return ASB;
    }

    // Map constants
    if( tc instanceof MapCon mc ) {
      String type = jtype(mc._t,false);
      ASB.p("new ").p(type).p("() {{ ");
      for( int i=0; i<mc._keys.length; i++ ) {
        ASB.p("put(");
        _value_tcon( mc._keys[i] ).p(",");
        _value_tcon( mc._vals[i] ).p("); ");
      }
      ASB.p("}} ");
      return ASB;
    }

    // Special TermTCon
    if( tc instanceof TermTCon ttc ) {
      ClassPart clz = ttc.clz();
      if( clz._name.equals("Console") && clz._path._str.equals("ecstasy/io/Console.x") )
        return ASB.p("_container.console()");
      throw XEC.TODO();      
    }
  
    throw XEC.TODO();
  }

}
