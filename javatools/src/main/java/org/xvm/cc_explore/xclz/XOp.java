package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;

import javax.lang.model.SourceVersion;

// Opcodes
public abstract class XOp {
  abstract void emit( XClzBuilder X );
  String _dst;                  // Destination name
  boolean _loop;                // True if a back branch targets here

  void pass2( XClzBuilder X ) { }

  void emit_dst( SB sb ) {
    if( _dst!=null ) sb.ip(_dst).p(" = ");
    emit2(sb);
    if( _dst!=null ) sb.p(";").nl();
  }
  void emit2( SB sb ) { }

  
  // --------------------------------------------------------
  // 0x01 - 0x04: Track line numbers
  public static class Line extends XOp {
    final int _n;
    Line( int n ) { _n=n; }
    @Override void emit( XClzBuilder X ) { }
  }

  // --------------------------------------------------------
  // 0x05: ENTER
  public static class Enter extends XOp {
    final int _lex_depth;
    Enter( XClzBuilder X ) {
      // I am assuming these basically enter/exit a Java lexical scope.
      // TODO: suspect this is a loop-header marker!
      _lex_depth = X._lexical_depth++;
    }
    @Override void emit( XClzBuilder X ) {
      // Not needed at the outermost scope, where the method scope has the same effect
      if( _lex_depth > 0 ) 
        X._sb.ip("{").ii().nl();
    }
  }

  // --------------------------------------------------------
  // 0x06: EXIT
  public static class Exit extends XOp {
    final int _lex_depth;
    Exit( XClzBuilder X ) {
      // I am assuming these basically enter/exit a Java lexical scope.
      // TODO: suspect this is a loop-header marker!
      _lex_depth = --X._lexical_depth;
    }
    @Override void emit( XClzBuilder X ) {
      // Not needed at the outermost scope, where the method scope has the same effect
      if( _lex_depth > 0 ) 
        X._sb.di().ip("}").nl();
    }
  }

  // --------------------------------------------------------
  // 0x20 - 0x22 - NVOK_00/1/N
  public static class NVOK_0 extends XOp {
    final String _self, _method;
    final boolean _toString;    // Primitives do not do ".toString" use "String.valueOf" instead
    NVOK_0( XClzBuilder X, int nrets ) {
      _self = X.rvalue();
      MethodCon mcon = (MethodCon)X.methcon();
      _method = mcon.name();
      _toString = _method.equals("toString") && mcon._par._par.part()._name.equals("Object");
      assert nrets==0 || nrets==1;
      _dst = nrets==1 ? X.lvalue() : null;
    }
    @Override void emit( XClzBuilder X ) { emit_dst(X._sb); }
    @Override void emit2(SB sb) {
      if( _toString ) sb.p("String.valueOf(").p(_self).p(")");
      else sb.p(_self).p(".").p(_method).p("()");
    }
  }

  // --------------------------------------------------------
  // 0x28 - 0x2A - NVOK_N0/1/N
  public static class NVOK_N extends XOp {
    final String _self, _method;
    final String[] _args;
    NVOK_N( XClzBuilder X, int nrets ) {
      _self = X.rvalue();
      MethodCon mcon = (MethodCon)X.methcon();
      _method = mcon.name();
      if( nrets!=0 ) throw XEC.TODO();
      int nargs = (int)X.pack64();
      _args = new String[nargs];
      for( int i=0; i<nargs; i++ )
        _args[i] = X.rvalue();
    }
    @Override void emit( XClzBuilder X ) {
      X._sb.ip(_self).p(".").p(_method).p("(");
        for( String arg : _args )
          if( arg!=null )
            X._sb.p( arg ).p( "," );
      X._sb.unchar().p(");").nl();
    }
  }

  
  // --------------------------------------------------------
  // 0x4C - 0x4E: RETURN_0, RETURN_1, RETURN_N
  public static class Return_N extends XOp {
    final int _n;
    Return_N( int n ) { _n=n; }
    @Override void emit( XClzBuilder X ) {
      if( _n!=0 ) throw XEC.TODO();  // Multi-return
      X._sb.ip("return;").nl();
    }
  }

  // --------------------------------------------------------
  // 0x50: VAR
  public static class Var extends XOp {
    final String _jtype, _name;
    boolean _fold;              // Fold with next op as initializer
    Var( XClzBuilder X ) {
      // Destination is read first and is typeaware, so read the destination type.
      _jtype = X.jtype_methcon();
      _name  = X.jname(_jtype);
      // Track active locals
      X.define(_name);
    }
    @Override void pass2( XClzBuilder X ) {
      XOp xop = X._xops[X._opn+1];
      if( xop._dst != null && xop._dst.equals(_name) ) {
        _fold = true;           // Next op is init, fold
        xop._dst = null;
      }
    }
    @Override void emit( XClzBuilder X ) {
      SB sb = X._sb;
      sb.ip(_jtype).p(" ").p(_name);
      if( _fold )               // Print inline and skip next op
        X._xops[++X._opn].emit2(sb.p(" = "));
      sb.p(";").nl();
    }
  } 

  // --------------------------------------------------------
  // 0x52: VAR_N
  public static class Var_N extends XOp {
    final String _jtype, _name;
    Var_N( XClzBuilder X ) {
      // Destination is read first and is typeaware, so read the destination type.
      _jtype = X.jtype_methcon();
      // Read the XTC variable name.  Must not collide with any other names
      _name = X.jname_methcon();
      X.define(_name);
    }
    @Override void emit( XClzBuilder X ) {
      X._sb.ip(_jtype).p(" ").p(_name).p(";").nl();
    }
  }

  // --------------------------------------------------------
  // 0x53: VAR_IN
  public static class Var_IN extends XOp {
    final String _jtype, _name, _jval;
    Var_IN( XClzBuilder X ) {
      // Destination is read first and is typeaware, so read the destination type.
      _jtype = X.jtype_methcon();
      // Read the XTC variable name.  Must not collide with any other names
      _name = X.jname_methcon();
      // RHS
      _jval = X.rvalue();
      X.define(_name);
    }
    @Override void emit( XClzBuilder X ) {
      X._sb.ip(_jtype).p(" ").p(_name).p(" = ").p(_jval).p(";").nl();
    }
  }
  
  // --------------------------------------------------------
  // 0x55: VAR_DN
  public static class Var_DN extends XOp {
    final String _jtype, _name, _jval;
    Var_DN( XClzBuilder X ) {
      // Destination is read first and is typeaware, so read the destination type.
      AnnotTCon anno = (AnnotTCon)X.methcon();
      // Read the XTC variable name.  Must not collide with any other names
      _name = X.jname_methcon();
      // TODO: Handle other kinds of typed args
      TermTCon ttc = anno.con().is_generic();
      if( ttc==null ) throw XEC.TODO();
      _jtype = X.jtype_ttcon(ttc);
      _jval  = X.jvalue_ttcon(ttc);    
      X.define(_name);
    }
    @Override void emit( XClzBuilder X ) {
      X._sb.ip(_jtype).p(" ").p(_name).p(" = ").p(_jval).p(";").nl();
    }
  }

  // --------------------------------------------------------
  // 0x60: MOV
  public static class Mov extends XOp {
    final String _src;
    Mov( XClzBuilder X ) {
      // TODO: Handle deferred semantics
      _src = X.rvalue();
      _dst = X.lvalue();
    }
    @Override void emit( XClzBuilder X ) { emit_dst(X._sb); }
    @Override void emit2(SB sb) { sb.p(_src); }
  }
  
  // --------------------------------------------------------
  // 0x6D: IS_EQ
  public static class IsEQ extends XOp {
    final String _val1, _val2, _jtype, _name;
    IsEQ( XClzBuilder X ) {
      _val1  = X.rvalue();
      _val2  = X.rvalue();
      _jtype = X.jtype_methcon();
      _name  = X.lvalue();
    }    
    @Override void emit( XClzBuilder X ) {
      X._sb.ip(_name).p(" = ").p(_val1);
      if( SourceVersion.isKeyword(_jtype) )  X._sb.p(" == ")    .p(_val2)       ;
      else                                   X._sb.p(".equals(").p(_val2).p(")");
      X._sb.p(";").nl();
    }
  }

  // --------------------------------------------------------
  // 0x79 Jump
  public static class Jump extends XOp {
    int _opn;
    boolean _exit_loop;
    Jump( XClzBuilder X ) {
      int off = (int)X.pack64();
      _opn = X._opn + off;
      if( off < 0 ) X._xops[_opn]._loop = true;
    }
    boolean back( int self ) { return self >= _opn; }
    @Override void pass2( XClzBuilder X ) {
      if( X._xops[_opn-1] instanceof Exit )
        _opn--;
      int self = X._opn;
      _exit_loop = !back(self) && exit_loop(X._xops,self,_opn);
    }
    static boolean exit_loop( XOp[] xops, int self, int target ) {
      assert self < target ;
      for( int x = self+1; x<target; x++ )
        if( xops[x] instanceof Jump jmp && jmp.back(x) )
          return true;
      return false;
    }
    @Override void emit( XClzBuilder X ) {
      if( back(X._opn) ) X._sb.di().ip("}").nl();
      else if( _exit_loop ) throw XEC.TODO();
      else X._sb.ip("GOTO ").p("L").p(_opn).p(";").nl();
    }
  }
  
  // --------------------------------------------------------
  // 0x7A JMP_TRUE
  public static class JumpTrue extends Jump {
    static String SRC;
    final String _src;
    JumpTrue( XClzBuilder X ) {
      super( psrc(X));
      _src = SRC;
    }
    static XClzBuilder psrc( XClzBuilder X ) { SRC = X.rvalue(); return X; }
    @Override void emit( XClzBuilder X ) {
      X._sb.ip("if( ").p(_src).p(" ) then ");
      if( _exit_loop ) X._sb.p("break;").nl();
      else throw XEC.TODO();
    }
  }
  
  // --------------------------------------------------------
  // 0x8F JMP_VAL_N
  public static class JMP_Val_N extends XOp {
    final String _name;         // Expression result name
    final int _narms;           // Number of switch arms
    final TCon[] _matches;      // Constants from method pool
    final int[] _opns;          // XCode opcode numbers for each arm, default, end
    final int _nregs;           // Number of registers needed for each arm
    final String[] _regs;       // Register for each test bit
    final long _isSwitch;       // Unused?
    
    JMP_Val_N( XClzBuilder X ) {
      // Generic Op_Switch
      _narms = X.u31();
      _matches = new TCon[_narms];
      _opns = new int[_narms+2];
      for( int i=0; i<_narms; i++ ) {
        _matches[i] = (TCon)X.methcon(); // Match constants; might be an array of constants
        _opns[i] = X._opn + (int)X.pack64(); // XCode offsets
      }
      _opns[_narms] = X._opn + (int)X.pack64(); // Default target
        
      // Specific to JMP_VAL_N
      _nregs = X.u31();
      _regs = new String[_nregs];
      _isSwitch = X.pack64();
      for( int i=0; i<_nregs; i++ )
        _regs[i] = X.rvalue(); // Becomes 4 / 5, guessing local variables, X1, X2
      _name = X.jname(null);
      X._locals.put(-1,_name); // -1 is special "local result"
    }

    // Confirm nicely laid out and reverse to an AST.
    @Override void pass2( XClzBuilder X ) {
      // Confirm end of each ARM except default forward jumps to same END op.
      // END op is after default (fall thru).
      // Each arm ENDS with a "dst" that is -1.
      int self = X._opn;
      for( int i=0; i<_narms+1; i++ )
        if( !(self < _opns[i]) )
          throw XEC.TODO();
      // Set the end
      int end_opn = _opns[_narms+1] = ((Jump)X._xops[_opns[1]-2])._opn;
      
      if( !(_opns[_narms] < end_opn) ) throw XEC.TODO();
      for( int i=1; i<_narms+1; i++ )
        if( end_opn != ((Jump)X._xops[_opns[i]-2])._opn )
          throw XEC.TODO();
      for( int i=0; i<_narms; i++ ) {
        // -3: -1 is Jump, -2 is Line, -3 better assign to _name
        XOp rez = X._xops[_opns[i+1]-3];
        if( rez._dst.equals(_name) ) rez._dst = null; // Print with expression syntax
        else throw XEC.TODO();                        // Not an AST layout
      }          
      // No Jump, no Line on the default
      XOp rez = X._xops[end_opn-1];
      if( rez._dst.equals(_name) ) rez._dst = null; // Print expression syntax
      else throw XEC.TODO();    // Not an AST layout
    }
    
    @Override void emit( XClzBuilder X ) {
      /*
       val expr =
         (x1==0 && x2==0) ? "FizzBuzz" :
         (x1==0) ? "Buzz" :
         (x2==0) ? "Fizz" :
         default();
      */
      X._sb.ip("val ").p(_name).p(" = ").ii().nl();
      
      // For each arm, emit guard test
      for( int i=0; i<_narms; i++ ) {
        X._sb.ip("(");             // Prelude
        TCon mcon = _matches[i];
        Const[] mvals;
        if( mcon instanceof AryCon arycon ) mvals = arycon.cons();
        else throw XEC.TODO();
        boolean more=false;
        for( int j=0; j<_nregs; j++ ) // Per-register test
          more = emit_guard( X._sb, _regs[j], mvals[j], more );
        X._sb.p(") ? ");
        // Emit code, skipping trailing Line, GOTO
        X.emit(_opns[i],_opns[i+1]-2);
        X._sb.p(" :").nl();
      }
      // Emit the default
      X._sb.i();
      X.emit(_opns[_narms],_opns[_narms+1]);
      X._sb.p(";").nl().di();
      assert X._opn == _opns[_narms+1]; // Ended at the END 
      X._opn--; // Adjust for following post-inc, start emitting at the END again
    }
    
    static private boolean emit_guard( SB sb, String reg, Const match, boolean more ) {
      if( match instanceof MatchAnyCon ) return more; // Skip "any" matches
      if( more ) sb.p(" && ");                     // Concatenate tests
      sb.p(reg).p(" == ").p(XClzBuilder.value_tcon((TCon)match));
      return true;
    }
  }

  // --------------------------------------------------------
  // 0x97 GP_MOD
  public static class BinOp extends XOp {
    final String _val1, _val2, _jop;
    BinOp( XClzBuilder X, String jop ) {
      _val1 = X.rvalue();
      _val2 = X.rvalue();
      _dst  = X.lvalue();
      _jop = jop;
    }
    @Override void emit( XClzBuilder X ) { emit_dst(X._sb); }
    @Override void emit2(SB sb) { sb.p(_val1).p(" ").p(_jop).p(" ").p(_val2); }
  }

  // --------------------------------------------------------
  // 0xAB IP_INC
  public static class IP_INC extends XOp {
    final int _inc;
    IP_INC( XClzBuilder X, int inc ) {
      _inc = inc;
      _dst = X.lvalue();
    }
    @Override void emit( XClzBuilder X ) {
      X._sb.ip(_dst).p(" += ").p(_inc).p(";").nl();
    }
  }
}
