package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xec.ecstasy.Comparable;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.collections.Hashable;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xtc.ast.*;
import org.xvm.xtc.cons.*;

import java.util.HashMap;
import java.util.HashSet;


// Some kind of base class for a Java class that implements an XTC Module
public class ClzBuilder {
  
  // XTC Module, which is the base Java class
  public final ModPart _mod;
  public final XType.Clz _tmod; // Module XType
  public static ClassPart CCLZ; // Compile unit class
  
  // XTC class, which is also the top-level Java class
  public final ClassPart _clz;
  public final XType.Clz _tclz; // Class XType
  
  // Top level, vs e.g. nested inner class.
  // Controls adding "import blah"
  public final boolean _top;

  // Java code emission for the java file header.
  // Includes imports discovered as we parse the XTC class
  public final SB _sbhead;
  
  // Java code emission for the whole top-level class
  public final SB _sb;
  
  // Fields for emitting a Method Code
  public MethodPart _meth;      // Method whose code is being parsed
  private CPool _pool;          // Parser for code buffer
  
  final HashMap<String,String> _names; // Java namification
  public final Ary<String> _locals; // Virtual register numbers to java names
  public final Ary<XType>  _ltypes; // Virtual register numbers to java types
  public final int nlocals() { return _locals._len; }

  // A collection of extra class source strings, generated all along and dumped
  // after the normal methods are dumped.
  public static HashMap<String,String> XCLASSES;

  // A collection of extra imports, generated all along
  public static HashSet<String> IMPORTS;
  
  // Make a nested java class builder
  public ClzBuilder( ClzBuilder bld, ClassPart nest ) { this(bld._mod,nest,bld._sbhead,bld._sb,false); }
  
  // Make a (possible nested) java class builder
  public ClzBuilder( ModPart mod, ClassPart clz, SB sbhead, SB sb, boolean top ) {
    if( top ) {
      IMPORTS = new HashSet<>();  // Top-level extra imports
      XCLASSES = new HashMap<>(); // Top-level extra classes
      CCLZ = mod;                 // Top-level compile unit
    }
    _mod = mod;
    _tmod = mod==null ? null : XType.Clz.make(mod);
    _clz = clz;
    _tclz = clz==null ? null : XType.Clz.make(clz);
    _top = top;
    _sbhead = sbhead;
    _sb = sb;
    _names = new HashMap<>();
    // TODO: Move to XMethBuilder
    _locals = new Ary<>(String.class);
    _ltypes = new Ary<>(XType .class);
  }
  
  // Fill in the body of the matching java class
  public void jclass( ) {
    assert _top;
    _sbhead.p("// Auto Generated by XEC from ").p( _mod._dir._str).p(_clz._path._str).nl().nl();
    _sbhead.p("package ").p(XEC.XCLZ).p(";").nl().nl();
    _sbhead.p("import ").p(XEC.ROOT).p(".xrun.*;").nl();
    _sbhead.p("import static ").p(XEC.ROOT).p(".xrun.XRuntime.$t;").nl();
    jclass_body( );
    // Output extra helper classes, if any
    for( String source : XCLASSES.values() )
      _sb.nl().p(source);
    for( String imp : IMPORTS )
      _sbhead.p("import ").p(XEC.XCLZ).p(".").p(imp).p(";").nl();
    _sbhead.nl();
    _clz._header = _sbhead;
    _clz._body = _sb;
    IMPORTS = null;
    XCLASSES = null;
    CCLZ = null;
  }
  
  public static String java_class_name( String xname ) { return xname; }
  public static String qual_jname( String xname ) { return XEC.XCLZ+"."+java_class_name(xname); }

  // Java Class body; can be nested (static inner class)
  private void jclass_body() {
    String java_class_name = java_class_name(_clz._name);
    _sb.nl();                   // Blank line
    _sb.ip("// XTC ").p(_top ? "module ": "class ").p(_clz._path._str).p(":").p(_clz._name).p(" as Java class ").p(java_class_name).nl();

    if( _top ) {
      _sb.p("public class ").p(java_class_name).p(" extends ").p(is_runclz() ? "XRunClz" : "XTC");
    } else {
      _sb.ip("public static class ").p(java_class_name).p(" extends ");
      if( _clz._super != null ) _sb.p(_clz._super._name);
      else if( _clz._f!=Part.Format.CONST ) _sb.p("XTC");
      else {
        _sb.p("Const");
        IMPORTS.add("ecstasy.Const");
        IMPORTS.add("ecstasy.Orderable.Ordered");
      } 
    }
    _sb.p(" {").nl().ii();

    
    // Required constructor to inject the container
    if( _top )
      _sb.ip("public ").p(java_class_name).p("( Container container ) { super(container); }").nl();
    
    // Get a unique KID for faster special dispath rules
    _sb.ifmt("static final int KID = GET_KID(new %0((Never)null));\n",java_class_name);
    _sb.ip("public int kid() { return KID; }\n");
    _sb.ifmt("private %0(Never n){super(n);}\n",java_class_name);
    
    // Look for a module/class init.  This will become the Java <clinit> / <init>
    MMethodPart mm = (MMethodPart)_clz.child("construct");
    MethodPart construct = (MethodPart)mm.child(mm._name);
    if( construct != null ) {
      assert _locals.isEmpty(); // No locals mapped yet
      assert construct._sibling==null;
      // Skip common empty constructor
      if( !construct.is_empty_function() ) {
        _sb.nl();
        if( _top ) {
          _sb.ip("static {").nl();
          ast(construct).jcode(_sb );
          _sb.ip("}").nl().nl();
        } else {
          _sb.i();
          jmethod_body(construct,java_class_name,true);
        }
      }
    }

    // Output Java methods for all class methods
    MethodPart run=null;
    for( Part part : _clz._name2kid.values() ) {
      switch( part ) {
      case MMethodPart mmp: 
        // Output java methods.
        // A bunch of methods that have special dispatch rules.
        String mname = mmp._name;
        MethodPart meth = (MethodPart)mmp.child(mname);
        boolean no_ast = meth==null || meth._ast == null;
        _sb.nl();               // Blank line between methods
        switch( mname ) {
        case "construct":
          continue;             // Constructors emitted first for easy reading
        case "equals":
          Comparable.make_equals(java_class_name,_sb);
          // Add a full default method
          if( no_ast ) Comparable.make_equals_default(_clz,java_class_name,_sb);
          else jmethod(meth,mname+"$"+java_class_name);
          break;
        case "compare":
          Orderable.make_compare(java_class_name,_sb);
          // Add a full default method
          if( no_ast ) Orderable.make_compare_default(_clz,java_class_name,_sb);
          else jmethod(meth,mname+"$"+java_class_name);
          break;
        case "hashCode":
          Hashable.make_hashCode(java_class_name,_sb);
          // Add a full default method
          if( no_ast ) Hashable.make_hashCode_default(_clz,java_class_name,_sb);
          else jmethod(meth,mname+"$"+java_class_name);
          break;
        default:
          // Save the top-level run method
          if( mname.equals("run") ) run = meth; 
          // Generate the method from the AST
          jmethod(meth,mname);
          break;
        }
        break;

      case PackagePart pack: 
        // Imports
        assert pack._contribs.length==1 && pack._contribs[0]._comp==Part.Composition.Import;
        // Ignore default ecstasy import
        TermTCon ttc = (TermTCon)pack._contribs[0]._tContrib;
        if( S.eq(ttc.name(),"ecstasy.xtclang.org") )
          break;
        // Other imports
        String qimport = qual_jname(ttc.name());
        _sbhead.p("import ").p(qimport).p(";").nl();
        ClzBldSet.add((ModPart)ttc.part(),(ModPart)ttc.part());
        break;
        
      case PropPart pp:
        // <clinit> for a static global property, or a normal Java field.        
        // If the property is in my superclass chain, and is ALSO defined here,
        // I need to override the super on just the present properties.
        if( _clz._super!=null && _clz._super.child(pp._name)!=null ) {
          if( pp._name2kid!=null ) throw XEC.TODO();
          break;                // Redundantly mentioned here
        }
        PropBuilder.make_class(this,pp);
        break;
        
      case ClassPart clz_nest:
        // Nested class.  Becomes a java static inner class
        ClzBuilder X = new ClzBuilder(this,clz_nest);
        X.jclass_body();
        break;
        
      default:
        throw XEC.TODO();
      }
    }

    // Const classes get a specific {toString, appendTo}, although they are not
    // mentioned if default
    if( _clz._f == Part.Format.CONST ) {
      if( _clz.child("toString")==null ) Const.make_toString(_clz,_sb);
    }
    
    // If the run method has a string array arguments -
    // - make a no-arg run, which calls the arg-run with nulls.
    // - make a main() which forwards to the arg-run
    if( _top && run != null && run._args != null ) {
      _sb.ip("public void run() { run(new Ary<String>(new String[0])); }").nl();
      _sb.ip("public void main(String[] args) {").nl().ii();
      _sb.ip(" run( new Ary<String>(args) );").nl().di();
      _sb.ip("}").nl();
    }

    // End the class body
    _sb.di().ip("}").nl();
  }

  private boolean is_runclz() {
    for( Part part : _clz._name2kid.values() )
      if( part instanceof MMethodPart mmp && mmp._name.equals("run") )
        return true;
    return false;
  }
  
  // Emit a Java string for this MethodPart.
  // Already _sb has the indent set.
  public void jmethod( MethodPart m, String mname ) {
    assert _locals.isEmpty();   // No locals mapped yet
    _sb.i();
    int access = m._nFlags & Part.ACCESS_MASK;
    if( access==Part.ACCESS_PRIVATE   ) _sb.p("private "  );
    if( access==Part.ACCESS_PROTECTED ) _sb.p("protected ");
    if( access==Part.ACCESS_PUBLIC    ) _sb.p("public "   );
    if( (m._nFlags & Part.STATIC_BIT) != 0 ) _sb.p("static ");
    // Return type
    XType[] xrets = XType.xtypes(m._rets);
    if( xrets==null ) _sb.p("void ");
    else if( xrets.length == 1 ) xrets[0].p(_sb).p(' ');
    else if( m.is_cond_ret() ) {
      // Conditional return!  Passes the extra return in XRuntime$COND.
      // The m._rets[0] is the boolean
      xrets[1].p(_sb).p(' ');
    } else {
      throw XEC.TODO(); // Multi-returns will need much help
    }
    jmethod_body(m,mname,false);
  }

  // Name, argument list, body:
  //
  // ...method_name( int arg0, String arg1, ...) {
  //   ...indented_body
  //   }
  public void jmethod_body( MethodPart m, String mname, boolean constructor ) {
    // Argument list
    _sb.p(mname).p("( ");
    XType[] xargs = XType.xtypes(m._args);
    if( xargs!=null ) {
      for( int i = 0; i < xargs.length; i++ ) {
        Parameter p = m._args[i];
        xargs[i].p(_sb).p(' ').p(p._name).p(", ");
        define(p._name,xargs[i]);
      }
      _sb.unchar(2);
    }
    _sb.p(" ) ");
    // Parse the Body
    AST ast = ast(m);
    // If a constructor, move the super-call up front
    if( constructor && _clz._super!=null ) do_super((MultiAST)ast);
    // Wrap in required "{}"
    if( !(ast instanceof BlockAST) )
      ast = new BlockAST(ast);
    ast.jcode(_sb);
    _sb.nl();

    // Popped back to the original args
    assert (m._args==null ? 0 : m._args.length) == nlocals();
    pop_locals(0);

    if( m._name2kid != null )
      for( Part part : m._name2kid.values() ) {
        switch( part ) {
        case PropPart pp:
          PropBuilder.make_class(this,pp );
          break;
        case MMethodPart mmp:
          // Method-local nested methods.
          // Lambda expressions have already been inlined
          if( !mmp._name.equals("->") ) {
            // Recursively dump nested method
            MethodPart meth = (MethodPart)mmp.child(mmp._name);
            jmethod(meth,mname+"$"+mmp._name);
          }
          break;
        default: throw XEC.TODO();
        }
      }
  }

  private void do_super( MultiAST ast ) {
    for( int i=0; i<ast._kids.length; i++ ) {
      AST kid = ast._kids[i];
      if( kid instanceof CallAST call &&
          call._kids[0] instanceof ConAST con &&
          ((MethodCon)con._tcon).name().equals("construct") ) {
        // Move ith kid to the front; rename as "super"
        System.arraycopy(ast._kids,0,ast._kids,1,i);
        ast._kids[0] = kid;
        con._con = "super";
        return;
      }
    }
    // Must find super call
    throw XEC.TODO();
  }
  
  // An inlinable method; turn into java Lambda syntax:
  // From: "{ return expr }"
  // To:   "         expr   "
  public void jmethod_body_inline( MethodPart meth ) {
    if( meth._name2kid != null ) throw XEC.TODO();
    // Parse the method body
    AST ast = ast(meth);
    // Strip any single-block wrapper
    if( ast instanceof BlockAST blk ) {
      if( blk._kids.length>1 ) throw XEC.TODO();
      ast = blk._kids[0];
    }
    // Strip any return wrapper, just the expression
    if( ast instanceof ReturnAST ret )
      ast = ret._kids[0];
    ast.jcode(_sb);
  }
  
  
  public AST ast( MethodPart m ) {
    // Build the AST from bytes
    _meth = m;
    _pool = new CPool(m._ast,m._cons); // Setup the constant pool parser
    AST ast = AST.parse(this);
    // Set types in every AST
    ast.type();
    // Do any trivial restructuring
    ast.do_rewrite();
    // Final AST ready to print as Java
    return ast;
  }

  
  public int u8 () { return _pool.u8(); }  // Packed read
  public int u31() { return _pool.u31(); } // Packed read
  public long pack64() { return _pool.pack64(); }
  // Read an array of method constants
  public org.xvm.xtc.cons.Const[] consts() { return _pool.consts(); }
  public org.xvm.xtc.cons.Const[] sparse_consts( int len) { return _pool.sparse_consts(len); }
  // Read a single method constant, advancing parse pointer
  public org.xvm.xtc.cons.Const con() { return con(u31()); }
  // Read a single method constant
  public org.xvm.xtc.cons.Const con( int i) { return _meth._cons[i]; }

  // Read an array of AST kid terminals
  public AST[] kids() { return _kids(u31(),0); }
  public AST[] kids( int n ) { return _kids(n,0); }
  // Read an array of AST kid terminals, with a given bias (skipped elements are null).
  public AST[] kids_bias( int b ) { return _kids(u31(),b); }
  
  private AST[] _kids( int n, int bias ) {
    if( n+bias==0 ) return null;
    AST[] kids = new AST[n+bias];
    for( int i=0; i<n; i++ )
      kids[i+bias] = AST.ast_term(this);
    return kids;
  }


  // --------------------------------------------------------------------------

  public void define( String name, XType type ) {
    // Track active locals
    _locals.add(name);
    _ltypes.add(type);
  }
    // Pop locals at end of scope
  public void pop_locals(int n) {
    while( n < nlocals() ) {
      _ltypes.pop();
      _locals.pop();
    }
  }
}
