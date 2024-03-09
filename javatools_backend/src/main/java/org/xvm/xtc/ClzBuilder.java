package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xec.ecstasy.Comparable;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xec.ecstasy.Service;
import org.xvm.xec.ecstasy.collections.Array.Mutability;
import org.xvm.xec.ecstasy.collections.Hashable;
import org.xvm.xtc.ast.*;
import org.xvm.xtc.cons.*;

import java.util.HashMap;
import java.util.HashSet;


// Some kind of base class for a Java class that implements an XTC Module
public class ClzBuilder {
  
  // XTC Module, which is the base Java class
  public final ModPart _mod;
  public final XClz _tmod;      // Module XType
  public static ClassPart CMOD; // Compile unit module
  
  // XTC class, which is also the top-level Java class
  public final ClassPart _clz;
  public final XClz _tclz;      // Class XType
  public static ClassPart CCLZ; // Compile unit class

  // XTC Module vs XTC class
  public final boolean _is_module;
  
  // Top level, vs e.g. nested inner class.
  // Controls adding "import blah"
  public final boolean _is_top;

  // Java code emission for the java file header.
  // Includes imports discovered as we parse the XTC class
  public final SB _sbhead;
  
  // Java code emission for the whole top-level class
  public final SB _sb;
  
  // Fields for emitting a Method Code
  public MethodPart _meth;      // Method whose code is being parsed
  public ExprAST _expr;         // True if AST is parse inside an ExprAST, similar to a nested method
  private CPool _pool;          // Parser for code buffer
  
  public final Ary<String> _locals; // Virtual register numbers to java names
  public final Ary<XType>  _ltypes; // Virtual register numbers to java types
  public final int nlocals() { return _locals._len; }

  // A collection of extra imports, generated all along
  static HashSet<String> IMPORTS;
  // Import base names; collisions will require name munging
  static HashMap<String,String> BASE_IMPORTS;
  static HashSet<XClz> AMBIGUOUS_IMPORTS;
  
  // Make a nested java class builder
  public ClzBuilder( ClzBuilder bld, ClassPart nest ) { this(bld._mod,nest,bld._sbhead,bld._sb,false); }
  
  // Make a (possible nested) java class builder
  public ClzBuilder( ModPart mod, ClassPart clz, SB sbhead, SB sb, boolean is_top ) {
    _is_top = is_top;
    if( is_top ) {
      IMPORTS = new HashSet<>();  // Top-level imports
      BASE_IMPORTS = new HashMap<>();// Top-level import base names
      AMBIGUOUS_IMPORTS = new HashSet<>();
      CMOD = mod;                 // Top-level compile unit
    }
    _is_module = mod==clz;
    _mod = mod;
    CCLZ = clz==null ? mod : clz; // Compile unit class
    _tmod = mod==null ? null : XClz.make(mod);
    _clz = clz;
    _tclz = clz==null ? null : XClz.make(clz);
    assert clz==null || clz._tclz == _tclz;
    _sbhead = sbhead;
    _sb = sb;
    _locals = new Ary<>(String.class);
    _ltypes = new Ary<>(XType .class);
  }
  
  // Fill in the body of the matching java class
  public void jclass( ) {
    assert _is_top;
    _sbhead.p("// ---------------------------------------------------------------").nl();
    _sbhead.p("// Auto Generated by XEC from ").p( _mod._dir._str).p(_clz._path._str).nl().nl();
    // XTC modules are all unique at the same level (the Repo level).
    // XTC classes are unique within a module, but not across modules.
    // Java package name includes the XTC module name.
    _sbhead.p("package ").p(XEC.XCLZ).p(".").p(_tclz._pack).p(";").nl().nl();
    _sbhead.p("import " ).p(XEC.ROOT).p(".xec.*;").nl();
    _sbhead.p("import " ).p(XEC.ROOT).p(".xrun.*;").nl();
    _sbhead.p("import static ").p(XEC.ROOT).p(".xrun.XRuntime.$t;").nl();
    jclass_body( );
    _sb.p("// ---------------------------------------------------------------").nl();
    // Output imports in the header
    for( String imp : IMPORTS )
      _sbhead.p("import ").p(imp).p(";").nl();
    _sbhead.nl();
    _clz._header = _sbhead;
    _clz._body = _sb;
    // Reset and clear all the statics
    IMPORTS = null;
    BASE_IMPORTS = null;
    for( XClz clz : AMBIGUOUS_IMPORTS )
      clz._ambiguous = false;
    AMBIGUOUS_IMPORTS = null;
    CMOD = null;
    CCLZ = null;
  }
  
  // Java Class body; can be nested (static inner class)
  @SuppressWarnings("fallthrough")
  private void jclass_body() {
    String java_class_name = _tclz._name;
    _sb.nl();                   // Blank line
    boolean is_iface = _clz._f==Part.Format.INTERFACE;
    String jpart = is_iface ? "interface " : "class ";
    String xpart = _is_module ? "module ": jpart;
    _sb.ip("// XTC ").p(xpart).p(_clz._path._str).p(":").p(_clz._name).p(" as Java ").p(jpart).p(java_class_name).nl();

    // public static class CLZ extends ...

    _sb.ip("public ");
    if( !_is_top ) _sb.p("static ");
    _tclz.clz_generic(_sb.p(jpart),true,true).p(" ");

    // ... extends Const/XTC/etc
    if( _clz._super != null ) _sb.p("extends ").p(_clz._super._name);
    else if( is_iface ) ; // No extends
    else if( _clz._f==Part.Format.CONST ) {
      _sb.p("extends Const");
      IMPORTS.add(XEC.XCLZ+".ecstasy.Const");
      IMPORTS.add(XEC.XCLZ+".ecstasy.Ordered");
    } else if( _clz._f==Part.Format.SERVICE ) {
      _sb.p("extends Service");
      IMPORTS.add(XEC.XCLZ+".ecstasy.Service");
    } else {
      _sb.p("extends ").p(is_runclz() ? "XRunClz" : "XTC");
    }

    // ...implements IMPL
    if( _clz._contribs!=null ) {
      int once=0;
      for( Contrib c : _clz._contribs )
        if( c._comp==Part.Composition.Implements ) {
          XClz iclz = switch( c._tContrib ) {
          case ParamTCon ptc -> XClz.make(ptc);
          case TermTCon ttc -> XClz.make(ttc.clz());
          case VirtDepTCon vtc -> XClz.make(vtc.clz());
          default -> { throw XEC.TODO(); }
          };
          iclz.clz_generic(_sb.p((once++ == 0) ? (_clz._tclz.iface() ? " extends " : " implements ") : ", "),true,false);
          add_import(iclz);
        }
    }
    
    _sb.p(" {").nl().ii();

    
    // Get a GOLDen instance for faster special dispatch rules.
    // Use the sentinel "Never" type to get a true Java no-arg constructor
    if( !is_iface ) {
      _sb.ifmt("static final %0 GOLD = new %0((Never)null);\n",java_class_name);
      _sb.ifmt("private %0(Never n){super(n);} // A no-arg-no-work constructor\n",java_class_name);
    }
    
    // Force native methods to now appear, so signature matching can assume a
    // method always exists.
    for( Part part : _clz._name2kid.values() ) {
      if( part instanceof MMethodPart mmp )
        // Output java methods.
        if( mmp._name2kid==null ) {
          mmp.addNative();
        } else {
          MethodPart m = (MethodPart)mmp.child(mmp._name);
          if( m._cons != null )
            for( Const con : m._cons )
              if( con instanceof MethodCon meth ) {
                MMethodPart mm = (MMethodPart)meth._par.part();
                if( mm._name2kid==null )
                  mm.addNative();
              }
        }
    }

    
    // Look for a module/class init.  This will become the Java <clinit> / <init>
    MMethodPart mm = (MMethodPart)_clz.child("construct");
    if( mm != null &&
        // Interfaces in XTC can require a constructor with a particular signature.
        // Interfaces in Java can NOT - so just do not emit the signature.
        !is_iface ) {
      // Empty constructor is specified, must be added now - BECAUSE I already
      // added another constructor, so I do not get the default Java empty
      // constructor "for free"

      // This is an "empty" constructor: it takes required explicit type
      // parameters but does no other work.
      _sb.ifmt("public %0( ",java_class_name);
      for( int i=0; i<_tclz._tns.length; i++ )
        _sb.fmt("$%0 %0,",_tclz._tns[i]);
      _sb.unchar().p(" ) { // default XTC empty constructor\n").ii();
      _sb.ip("super((Never)null);").nl();
      for( int i=0; i<_tclz._tns.length; i++ )
        _sb.ifmt("this.%0 = %0;\n",_tclz._tns[i]);
      _sb.di().ip("}\n");
      
      // For all other constructors
      for( MethodPart meth = (MethodPart)mm.child(mm._name); meth != null; meth = meth._sibling ) {
        // To support XTC 'construct' I need a layer of indirection.  The Java
        // moral equivalent is calling 'this' in a constructor, but XTC allows
        // calling 'this' anywhere.
        
        // "public static Foo construct(typeargs,args) { return new Foo(typeargs).$construct(args).$check(); }"
        _sb.nl();
        keywords(meth,true);
        _tclz.clz_generic(_sb,false,true);
        _sb.fmt("%0 construct( ",java_class_name); // Return type
        for( int i=0; i<_tclz._tns.length; i++ )
          _sb.fmt("$%0 %0,",_tclz._tns[i]);
        _sb.unchar();
        args(meth,_sb);
        _sb.p(") { return new ").p(java_class_name).p("( ");
        for( int i=0; i<_tclz._tns.length; i++ )
          _sb.fmt("%0,",_tclz._tns[i]);
        _sb.unchar().p(").$construct(");
        arg_names(meth,_sb);
        if( !is_iface ) _sb.p(").$check(); }").nl();

        // "private Foo $construct(args) { ..."
        _sb.ip("private ").p(java_class_name).p(" ");
        // Common empty constructor
        if( meth.is_empty_function() ) {
          _sb.p("$construct(){ return this; }").nl();
        } else {
          jmethod_body(meth,"$construct",true);
        }
      }
    }

    // Output Java methods for all class methods
    for( Part part : _clz._name2kid.values() ) {
      switch( part ) {
      case MMethodPart mmp: 
        // Output java methods.
        String mname = jname(mmp._name);
        for( MethodPart meth = (MethodPart)mmp.child(mname); meth != null; meth = meth._sibling ) {
          if( S.eq(mname,"construct") )
            continue;           // Constructors emitted first for easy reading
          _sb.nl(); // Blank line between methods
          // A bunch of methods that have special dispatch rules.
          switch( mname ) {
          case "equals":
            Comparable.make_equals(java_class_name,_sb);
            // Add a full default method
            if( meth._ast == null ) Comparable.make_equals_default(_clz,java_class_name,_sb);
            else jmethod(meth,mname+"$"+java_class_name);
            break;
          case "compare":
            Orderable.make_compare(java_class_name,_sb);
            // Add a full default method
            if( meth._ast == null ) Orderable.make_compare_default(_clz,java_class_name,_sb);
            else jmethod(meth,mname+"$"+java_class_name);
            break;
          case "hashCode":
            Hashable.make_hashCode(java_class_name,_sb);
            // Add a full default method
            if( meth._ast == null ) Hashable.make_hashCode_default(_clz,java_class_name,_sb);
            else jmethod(meth,mname+"$"+java_class_name);
            break;
          case "estimateStringLength":
          case "appendTo":
            if( meth._ast==null ) // No body, but declared.  Use the interface default.
              break;
            //noinspection fallthrough
          default:
            // Generate the method from the AST
            jmethod(meth,mname);
            // Service classes generate $mname and $$mname wrapper methods.
            if( _tclz.isa(XCons.SERVICE) && meth.isPublic() )
              Service.make_methods(meth,java_class_name,_sb);
            break;
          }
        }
        break;

      case PackagePart pack:
        // Will need to compile the package at the same time this module.
        if( pack._contribs == null )
          break;
        // Imports
        assert pack._contribs.length==1 && pack._contribs[0]._comp==Part.Composition.Import;
        // Ignore default ecstasy import
        TermTCon ttc = (TermTCon)pack._contribs[0]._tContrib;
        if( S.eq(ttc.name(),"ecstasy.xtclang.org") )
          break;
        // Other imports
        add_import(ttc.clz());
        break;
        
      case PropPart pp:
        PropBuilder.make_class(this,pp);
        break;
        
      case ClassPart clz_nest:
        // Nested class.  Becomes a java static inner class
        ClzBuilder X = new ClzBuilder(this,clz_nest);
        X.jclass_body();
        break;

      case TDefPart typedef:
        // TypeDef.  Hopefully already swallowed into XType/XClz
        break;
        
      default:
        throw XEC.TODO();
      }
    }

    // Const classes get a specific {toString, appendTo}, although they are not
    // mentioned if default
    if( _clz._f == Part.Format.CONST &&
        _clz.child("toString")==null )
      org.xvm.xec.ecstasy.Const.make_toString(_clz,_sb);
    
    // If the run method has a string array arguments -
    // - make a no-arg run, which calls the arg-run with nulls.
    // - make a main() which forwards to the arg-run
    if( _is_module ) {
        Part prun = _clz._name2kid.get("run");
        if( prun!=null ) {
          MethodPart run =  (MethodPart)(((MMethodPart)prun)._name2kid.get("run"));
          if( run._args != null ) {
            add_import(XCons.ARYSTRING);
            _sb.ip("public void run() { run(new AryString()); }").nl();
            _sb.ip("public void main( AryString args ) { run( args ); }").nl();
          }
        }
    }
  
    // Check fields definitely assigned.
    // Freeze if const.
    if( !is_iface ) {
      _sb.ip("private ").p(java_class_name).p(" $check() {").nl().ii();;
      XType xt;
      for( Part part : _clz._name2kid.values() )
        if( part instanceof PropPart prop && 
            prop.isField() && 
            (xt=XType.xtype(prop._con,false))._notNull ) {
          // if( fld==null ) throw new IllegalState;
          _sb.ifmt("if( %0==null ) throw new XTC.IllegalState(\"Did not initialize %0\");",prop._name).nl();
          // If Const, freeze fields
          if( _clz._f == Part.Format.CONST && !xt.primeq() ) {
            // if( fld.mutability$getOrd()!=0 ) {
            //   if( fld instanceof Freezable frz ) fld = (clz)frz.freeze(false);
            //   else throw new IllegalState;
            // }
            add_import(XCons.FREEZABLE);
            _sb.ifmt("if( %0.mutability$getOrd()!=%1 ) {\n",prop._name,Mutability.Constant.ordinal()).ii();
            _sb.ifmt(  "if( %0 instanceof Freezable frz ) %0 = (%1)frz.freeze(false);\n",prop._name,xt.clz());
            _sb.ifmt(  "else throw new XTC.IllegalState(\"'%0' is neither frozen nor Freezable\");\n",prop._name);
            _sb.di().p("}\n");
          }
        }
      _sb.ip("return this;").nl();
      _sb.di().ip("}").nl();
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

  // Print out the function header keywords.
  // Get XTypes for args, rets
  private void keywords(MethodPart m, boolean is_constructor) {
    _sb.i();
    int access = m._nFlags & Part.ACCESS_MASK;
    // Public service methods get rewritten to private
    if( _tclz.isa(XCons.SERVICE) && !is_constructor && !S.eq(m._name,"toString") )
      access = Part.ACCESS_PRIVATE;
    if( access==Part.ACCESS_PRIVATE   ) _sb.p("private "  );
    if( access==Part.ACCESS_PROTECTED ) _sb.p("protected ");
    if( access==Part.ACCESS_PUBLIC &&  !_tclz.iface() ) _sb.p("public "   ); 
    if( m.isStatic() )      _sb.p("static ");
    if( m._ast.length==0 )  _sb.p("abstract ");
    else if( _tclz.iface() ) _sb.p("default ");

    // XType the args and rets
    m._xargs = XType.xtypes(m._args);
    m._xrets = XType.xtypes(m._rets);
  }

  // Argument with types "( int arg0, String arg1, ... )"
  static public void args(MethodPart m, SB sb) {
    // Argument list
    if( m._xargs!=null ) {
      for( int i = 0; i < m._xargs.length; i++ ) {
        // Unbox boxed args
        m._xargs[i] = m._xargs[i].unbox();
        if( m._xargs[i] instanceof XClz xclz )
          add_import(xclz);
        // Parameter class, using local generic parameters
        Parameter p = m._args[i];
        if( p.tcon() instanceof TermTCon ttc && ttc.id() instanceof FormalCon )
          sb.p("$").p(ttc.name());
        else if( p._special )
          sb.p("$").p(p._name);
        else 
          m._xargs[i].clz(sb,p.tcon() instanceof ParamTCon ptc ? ptc : null);

        // Parameter name, and define it in scope
        sb.p(' ').p(p._name).p(", ");
      }
      sb.unchar(2);
    }
  }
  
  // Argument names "arg0, arg1, ..."
  static public void arg_names(MethodPart m, SB sb) {
    if( m._args!=null ) {
      for( Parameter p : m._args )
        sb.p(p._name).p(", ");
      sb.unchar(2);
    }
  }

  // Return signature; stuff like "long " or "<T extends BAZ> HashSet<T> "
  public static SB ret_sig( MethodPart m, SB sb ) {
    // Check for XTC generic method, needing a Java generic type
    if( m._args!=null && m._args.length>1 && m._args[0]._special ) {
      // TODO: XClz.clz_def
      sb.p("<");
      // Check the type-parm parm flag, and insert Java generics for all
      for( int i=0; i<m._args.length; i++ )
        if( m._args[i]._special ) {
          sb.p("$").p(m._args[i]._name).p(" extends ");
          if( ((XClz)m._xargs[i]).iface() ) sb.p("XTC & ");
          m._xargs[i].clz(sb).p(", ");
        }
      sb.unchar(2).p("> ");
    }
    
    // Return type
    if( m._xrets==null ) sb.p("void ");
    else {
      XType xret = 
        m._xrets.length == 1 ?  m._xrets[0] :
        // Conditional return!  Passes the extra return in XRuntime$COND.
        // The m._rets[0] is the boolean
        m.is_cond_ret() ? m._xrets[1] :
        // Tuple multi-return
        XCons.make_tuple(m._xrets);
      xret.clz(sb).p(' ');
    }

    return sb;
  }
  
  // Emit a Java string for this MethodPart.
  // Already _sb has the indent set.
  // "public static <G extends Generic> Ary<G> " ... jmethod_body()
  public void jmethod( MethodPart m, String mname ) {
    assert _locals.isEmpty();   // No locals mapped yet

    // Print out the function header keywords
    keywords(m,false);
    
    // Return signature; stuff like "long " or "<T extends BAZ> HashSet<T> "
    ret_sig(m, _sb);
    
    jmethod_body(m,mname,false);
  }

  // Name, argument list, body:
  //
  // ...method_name( int arg0, String arg1, ...) {
  //   ...indented_body
  // }
  public void jmethod_body( MethodPart m, String mname, boolean constructor ) {
    // Argument list:
    // ... method_name( int arg0, String, arg1, ... ) {
    _sb.p(mname).p("( ");
    args(m,_sb);
    _sb.p(" ) ");
    // Define argument names
    if( m._xargs!=null )
      for( int i = 0; i < m._xargs.length; i++ )
        define(m._args[i]._name,m._xargs[i]);
    
    // Abstract method, no body
    if( m._ast.length==0 ) {
      _sb.p(";").nl();
    } else {
    
      // Parse the Body
      AST ast = ast(m);
      // If a constructor, move the super-call up front
      if( constructor && _clz._super!=null && ast instanceof MultiAST multi )
        do_super(multi);
      // Wrap in required "{}" block
      BlockAST blk = ast instanceof BlockAST blk0 ? blk0 : new BlockAST(ast);
      // This is a XTC constructor, which in Java is implemented as a factory
      // method - which has the normal no-arg Java constructor already called -
      // but now the XTC constructor method needs to end in a Java return.
      if( constructor )
        blk = blk.add(new ReturnAST(null,m,null,new RegAST(-5/*A_THIS*/,"this",_tclz)));
      blk.jcode(_sb);
      _sb.nl();
    }

    // Popped back to the original args
    assert (m._args==null ? 0 : m._args.length) == nlocals();
    pop_locals(0);

    if( m._name2kid != null )
      for( Part part : m._name2kid.values() ) {
        switch( part ) {
        case PropPart pp:
          PropBuilder.make_class(this,pp);
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
        case ClassPart clz_nest:
          // Nested class.  Becomes a java static inner class
          ClzBuilder X = new ClzBuilder(this,clz_nest);
          X.jclass_body();
          break;
        default: throw XEC.TODO();
        }
      }
  }

  // Constructors call other constructors via factory method $constructor,
  // skipping the allocation step.
  private void do_super( MultiAST ast ) {
    for( int i=0; i<ast._kids.length; i++ ) {
      AST kid = ast._kids[i];
      if( kid instanceof CallAST call &&
          call._kids[0] instanceof ConAST con &&
          ((MethodCon)con._tcon).name().equals("construct") ) {
        kid._kids[0] = new RegAST(-13,"super",XCons.XXTC/*_tclz._super*/);
        ast._kids[i] = new InvokeAST("$construct",new XType[]{XCons.VOID},kid._kids);
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


  public static XClz add_import( ClassPart clz ) {
    return add_import(XClz.make(clz));
  }
  public static XClz add_import( XClz tclz ) {
    // Nested internal class; imports recorded elsewhere
    if( ClzBuilder.IMPORTS==null ) return tclz;
    // If the compiling class has the same path, tclz will be compiled in the
    // same source code
    if( tclz._clz!=null && tclz._clz._path!=null && S.eq(CCLZ._path._str,tclz._clz._path._str) )
      return tclz;
    // External; needs an import
    if( tclz.needs_import(true) ) {
      // Check for colliding on the base names; means we have to use the fully
      // qualified name everywhere.
      String tqual = tclz.qualified_name();
      String old = ClzBuilder.BASE_IMPORTS.putIfAbsent(tclz.name(),tqual);
      if( old!=null && !S.eq(old,tqual) ) {
        tclz._ambiguous=true;   // Use fully qualified name
        ClzBuilder.AMBIGUOUS_IMPORTS.add(tclz); // Reset the ambiguous flag after compilation
      } else {
        // Import the qualified name, but use the shortcut name everywhere
        ClzBuilder.IMPORTS.add(tqual);
      }
      // Needs a build also
      if( tclz.needs_build() )
        ClzBldSet.add(tclz._clz);
    }
    return tclz;
  }

  public static void add_import( String s ) {
    IMPORTS.add(s);
  }
  
  // --------------------------------------------------------------------------

  public AST ast( MethodPart m ) {
    // Pre-cook XType returns
    if( m._xrets==null ) m._xrets = XType.xtypes(m._rets);
    // Build the AST from bytes
    _meth = m;
    _pool = new CPool(m._ast,m._cons); // Setup the constant pool parser
    AST ast = AST.parse(this);
    // Set types in every AST
    ast.type();
    // Do any trivial restructuring
    ast.doRewrite(null);
    AST._uid=0;                        // Reset temps for next go-round
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
  public org.xvm.xtc.cons.Const con( int i) { return i==-1 ? null : _meth._cons[i]; }

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

  public int define( String name, XType type ) {
    // Track active locals
    _locals.add(name);
    _ltypes.add(type);
    return _locals._len-1;      // Return register number
  }
    // Pop locals at end of scope
  public void pop_locals(int n) {
    while( n < nlocals() ) {
      _ltypes.pop();
      _locals.pop();
    }
  }

  // Name mangle for java rules
  public static String jname( String name ) {
    // Mangle names colliding with java keywords
    if( switch( name ) {
    case "default" -> true;
    case "assert" -> true;
    default -> false;
      } )
      name += "0";
    return name;
  }
}
