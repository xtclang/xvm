package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.xrun.Container;
import org.xvm.cc_explore.xrun.NativeContainer;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.*;

import java.lang.Character;
import java.util.HashMap;
import javax.lang.model.SourceVersion;
import java.lang.reflect.Constructor;

// Some kind of base class for a Java class that implements an XTC Module
public class XClzBuilder {
  public final ModPart _mod;
  public final SB _sb;
  public XClz _xclz;            // Java class

  // Fields for emitting a Method Code
  public MethodPart _meth;      // Method whose code is being parsed
  private CPool _pool;          // Parser for code buffer
  final HashMap<String,String> _names; // Java namification
  final NonBlockingHashMapLong<String> _locals, _ltypes; // Virtual register numbers to java names
  int _nlocals;                 // Number of locals defined; popped when lexical scopes pop
  
  public XClzBuilder( ModPart mod ) {
    System.err.println("Making XClz for "+mod);
    _mod = mod;
    _sb = new SB();
    _names = new HashMap<>();
    _locals = new NonBlockingHashMapLong<>();
    _ltypes = new NonBlockingHashMapLong<>();

    // Let's start by assuming if we're here, we're inside the top-level
    // ecstasy package - otherwise we're nested instead the mirror for the
    // containing package.
    assert mod.child("ecstasy") instanceof PackagePart;

    // The Java class will extend XClz.
    // The Java class name will be the mangled module class-name.
    String java_class_name = "J"+mod._name;
    jclass_body(java_class_name);
    System.out.println(_sb);
    
    //System.out.println(_sb);
    try {
      Class<XClz> clz = XClzCompiler.compile("org.xvm.cc_explore.xclz."+java_class_name, _sb);
      Constructor<XClz> xcon = clz.getConstructor(Container.class);
      XClz xclz = xcon.newInstance(new NativeContainer());
      xclz.run();
    } catch( Exception ie ) {
      throw XEC.TODO();
    }
  }

  // Fill in the body of the matching java class
  private void jclass_body( String java_class_name ) {
    _sb.p("// Auto Generated by XEC from ").p(_mod._dir._str).p(_mod._path._str).nl().nl();
    _sb.p("package org.xvm.cc_explore.xclz;").nl().nl();
    _sb.p("import org.xvm.cc_explore.xrun.*;").nl();
    _sb.p("import static org.xvm.cc_explore.xrun.XRuntime.$t;").nl();
    _sb.nl();
    _sb.p("public class ").p(java_class_name).p(" extends XClz {").nl().ii();

    // Required constructor to inject the container
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
        jcode_ast(construct);
        _sb.ip("}").nl().nl();
      }
    }

    // Output Java methods for all Module methods
    // TODO: Classes in a Module?
    for( Part part : _mod._name2kid.values() ) {
      if( part instanceof MMethodPart mmp ) {
        if( mmp._name.equals("construct") ) continue; // Already handled module constructor
        MethodPart meth = (MethodPart)mmp.child(mmp._name);
        jmethod(meth);
      } else if( part instanceof PackagePart ) {
        // Self module is OK
      } else {
        throw XEC.TODO();
      }
    }

    // End the class body
    _sb.di().p("}").nl();
  }
  
  // Emit a Java string for this MethodPart.
  // Already _sb has the indent set.
  private void jmethod( MethodPart m ) {
    assert _locals.isEmpty() && _nlocals==0; // No locals mapped yet
    _sb.ip("public ");
    // Return type
    if( m._rets==null ) _sb.p("void ");
    else if( m._rets.length == 1 ) _sb.p(jtype_tcon(m._rets[0]._con,false)).p(' ');
    else throw XEC.TODO(); // Multi-returns will need much help
    // Argument list
    _sb.p(m._name).p("( ");
    if( m._args!=null ) {
      for( int i = 0; i < m._args.length; i++ ) {
        Parameter p = m._args[i];
        _sb.p(jtype_tcon(p._con,false)).p(' ').p(p._name).p(", ");
        _locals.put(i,p._name);
        _nlocals++;
      }
      _sb.unchar(2);
    }
    _sb.p(" ) ");
    // Body    
    jcode_ast(m);
    _sb.nl();

    // Popped back to the original args
    assert (m._args==null ? 0 : m._args.length) == _nlocals;
    pop_locals(0);
  }

  private void jcode_ast( MethodPart m ) {
    // Build the AST from bytes
    _meth = m;
    _pool = new CPool(m._ast,m._cons); // Setup the constant pool parser
    AST ast = AST.parse(this);
    // Do any trivial restructuring
    ast = ast.do_rewrite();
    // Pretty print as Java
    ast.jcode(_sb );
  }

  
  int u8 () { return _pool.u8(); }  // Packed read
  int u31() { return _pool.u31(); } // Packed read
  long pack64() { return _pool.pack64(); }
  String utf8() { return _pool.utf8(); }
  Const[] consts() { return _pool.consts(); }
  Const con(int i) { return _meth._cons[i]; }


  // --------------------------------------------------------------------------

  void define( String name, String type ) {
    // Track active locals
    _locals.put(_nlocals  ,name);
    _ltypes.put(_nlocals++,type);
  }
    // Pop locals at end of scope
  void pop_locals(int n) {
    while( n < _nlocals )
      _locals.remove(--_nlocals);
  }

  // Magic constant for indexing into the constant pool.
  static final int CONSTANT_OFFSET = -16;
  // Read a method constant.  Advances the parse point.
  Const methcon(long idx) {
    // CONSTANT_OFFSET >= idx: uses a method constant
    assert idx <= CONSTANT_OFFSET && ((int)idx)==idx;
    return _meth._cons[CONSTANT_OFFSET - (int)idx];
  }

  Const methcon_ast() { return methcon_ast((int)pack64()); }
  Const methcon_ast(int idx) { return _meth._cons[idx]; }
  
  // Return a java-valid name
  String jname_methcon_ast( ) {
    String xname = ((StringCon)methcon_ast())._str;
    return jname(xname);
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
      put("Console+ecstasy/io/Console.x","XConsole");
      put("Int64+ecstasy/numbers/Int64.x","long");
      put("Boolean+ecstasy/Boolean.x","boolean");
      put("StringBuffer+ecstasy/text/StringBuffer.x","StringBuffer");
    }};
  
  // Produce a java type from a method constant
  String jtype_methcon_ast() { return jtype_tcon( (TCon)methcon_ast(), false ); }
  // Produce a java type from a TermTCon
  static String jtype_tcon( TCon tc, boolean boxed ) {
    if( tc instanceof TermTCon ttc ) {
      ClassPart clz = (ClassPart)ttc.part();
      String key = clz._name + "+" + clz._path._str;
      String val = XJMAP.get(key);
      if( val!=null )
        return boxed ? val.substring(0,1).toUpperCase() + val.substring(1) : val;
      throw XEC.TODO();
    }
    if( tc instanceof ParamTCon ptc ) {
      String telem = jtype_tcon(ptc._parms[0],true);
      ClassPart clz = ((ClzCon)ptc._con).clz();
      if( clz._name.equals("Array") && clz._path._str.equals("ecstasy/collections/Array.x") ) {
        if( telem.equals("Long") )  return "XAryI64"; // Java ArrayList specialized to int64
        return "XAry<"+telem+">"; // Shortcut class
      }
      if( clz._name.equals("Range") && clz._path._str.equals("ecstasy/Range.x") ) {
        if( telem.equals("Long") ) return "XRange"; // Shortcut class
        else throw XEC.TODO();
      }
      if( clz._name.equals("List") && clz._path._str.equals("ecstasy/collections/List.x") )
        return "XAry<"+telem+">"; // Shortcut class
      if( clz._name.equals("Tuple") && clz._path._str.equals("ecstasy/collections/Tuple.x") ) {
        if( ptc._parms.length==1 )  // Tuple of length 1 ?
          return "XAry<"+telem+">"; // Shortcut class
        throw XEC.TODO();
      }
      if( clz._name.equals("Function") && clz._path._str.equals("ecstasy/reflect/Function.x") )
        // TODO: Gonna need more type info that this
        return "XFunc";
         
      throw XEC.TODO();
    }
    if( tc instanceof ImmutTCon itc ) 
      return jtype_tcon(itc.icon(),boxed); // Ignore immutable for now
    
    throw XEC.TODO();
  }  

  // Produce a java value from a TCon
  private static final SB ASB = new SB();
  static public String value_tcon( TCon tc ) {
    assert ASB.len()==0;
    value_tcon(tc,false);
    String rez = ASB.toString();
    ASB.clear();
    return rez;
  }
  private static SB value_tcon( TCon tc, boolean nested ) {
    // Integer constants
    if( tc instanceof IntCon ic ) {
      if( ic._big != null ) throw XEC.TODO();
      ASB.p(ic._x);
      return (int)ic._x == ic._x ? ASB : ASB.p('L');
    }
    
    // String constants
    if( tc instanceof StringCon sc )
      return ASB.p('"').p(sc._str).p('"');
    
    // Array constants
    if( tc instanceof AryCon ac ) {
      assert ac.type() instanceof ImmutTCon; // Immutable array goes to static
      String type = jtype_tcon(ac.type(),false);
      ASB.p("new ").p(type).p("()");
      if( ac.cons()!=null ) {
        ASB.p(" {{ ");
        for( Const con : ac.cons() ) {
          ASB.p("add(");
          value_tcon( (TCon)con, true );
          ASB.p("); ");
        }
        ASB.p("}}");
      }
      return ASB;
    }
    
    // Booleans
    if( tc instanceof EnumCon econ ) {
      ClassPart clz = (ClassPart)econ.part();
      if( !clz._super._name.equals("Boolean") ) throw XEC.TODO();
      return ASB.p(clz._name.equals("False") ? "false" : "true");
    }
    
    // Literal constants
    if( tc instanceof LitCon lit )
      return ASB.p(lit._str);
    // Method constants
    if( tc instanceof MethodCon mcon )  {
      MethodPart meth = (MethodPart)mcon.part();
      // TODO: Assumes the method is in the local Java namespace
      String name = meth._name;
      return ASB.p(name);
    }
    throw XEC.TODO();
  }

  // Produce a java value from a TermTCon
  static String jvalue_ttcon( TermTCon ttc ) {
    ClassPart clz = ttc.clz();
    if( clz._name.equals("Console") && clz._path._str.equals("ecstasy/io/Console.x") )
      return "_container.console()";
    throw XEC.TODO();
  }  
}
