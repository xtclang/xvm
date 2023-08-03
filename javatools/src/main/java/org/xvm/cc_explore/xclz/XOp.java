package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;

import javax.lang.model.SourceVersion;

// Opcodes
public abstract class XOp {
  abstract void emit( SB sb );

  // --------------------------------------------------------
  // 0x01 - 0x04: Track line numbers
  public static class Line extends XOp {
    final int _n;
    Line( int n ) { _n=n; }
    @Override void emit( SB sb ) {}
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
    @Override void emit( SB sb ) {
      // Not needed at the outermost scope, where the method scope has the same effect
      if( _lex_depth > 0 ) 
        sb.ip("{").ii().nl();
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
    @Override void emit( SB sb ) {
      // Not needed at the outermost scope, where the method scope has the same effect
      if( _lex_depth > 0 ) 
        sb.di().ip("}").nl();
    }
  }

  // --------------------------------------------------------
  // 0x20 - 0x22 - NVOK_00/1/N
  public static class NVOK_0 extends XOp {
    final String _self, _method, _dst;
    NVOK_0( XClzBuilder X, int nrets ) {
      _self = X.rvalue();
      MethodCon mcon = (MethodCon)X.methcon();
      _method = mcon.name();
      assert nrets==0 || nrets==1;
      _dst = nrets==1 ? X.lvalue() : null;
    }
    @Override void emit( SB sb ) {
      sb.ip(_dst).p(" = ").p(_self).p(".").p(_method).p("();").nl();
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
    @Override void emit( SB sb ) {
      sb.ip(_self).p(".").p(_method).p("(");
      for( int i=0; i<_args.length; i++ )
        sb.p(_args[i]).p(",");
      sb.unchar().p(");").nl();
    }
  }

  
  // --------------------------------------------------------
  // 0x4C - 0x4E: RETURN_0, RETURN_1, RETURN_N
  public static class Return_N extends XOp {
    final int _n;
    Return_N( int n ) { _n=n; }
    @Override void emit( SB sb ) {
      if( _n!=0 ) throw XEC.TODO();  // Multi-return
      sb.ip("return;").nl();
    }
  }

  // --------------------------------------------------------
  // 0x50: VAR
  public static class Var extends XOp {
    final String _jtype, _name;
    Var( XClzBuilder X ) {
      // Destination is read first and is typeaware, so read the destination type.
      _jtype = X.jtype_methcon();
      _name  = X.jname(_jtype);
      // Track active locals
      X.define(_name);
    }
    @Override void emit( SB sb ) {  sb.ip(_jtype).p(" ").p(_name).p(";").nl();   }
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
    @Override void emit( SB sb ) {  sb.ip(_jtype).p(" ").p(_name).p(";").nl();  }
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
    @Override void emit( SB sb ) {
      sb.ip(_jtype).p(" ").p(_name).p(" = ").p(_jval).p(";").nl();
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
    @Override void emit( SB sb ) {
      sb.ip(_jtype).p(" ").p(_name).p(" = ").p(_jval).p(";").nl();
    }
  }

  // --------------------------------------------------------
  // 0x60: MOV
  public static class Mov extends XOp {
    final String _src, _dst;
    Mov( XClzBuilder X ) {
      // TODO: Handle deferred semantics
      _src = X.rvalue();
      _dst = X.lvalue();
    }
    @Override void emit( SB sb ) {
      sb.ip(_dst).p(" = ").p(_src).p(";").nl();
    }
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
    @Override void emit( SB sb ) {
      sb.ip(_name).p(" = ").p(_val1);
      if( SourceVersion.isKeyword(_jtype) )  sb.p(" == ")    .p(_val2)       ;
      else                                   sb.p(".equals(").p(_val2).p(")");
      sb.p(";").nl();
    }
  }

  // --------------------------------------------------------
  // 0x79 Jump
  public static class Jump extends XOp {
    final int _opn;
    Jump( XClzBuilder X ) {
      _opn = X._opn + (int)X.pack64();
      X._jmp_to.set(_opn);
    }
    @Override void emit( SB sb ) {
      sb.ip("GOTO L").p(_opn).p(";").nl();
    }
  }
  
  // --------------------------------------------------------
  // 0x7A JMP_TRUE
  public static class JumpTrue extends XOp {
    final String _src;
    final int _opn;
    JumpTrue( XClzBuilder X ) {
      _src = X.rvalue();
      _opn = X._opn + X.u31();
      X._jmp_to.set(_opn);
    }
    @Override void emit( SB sb ) {
      sb.ip("if( ").p(_src).p(" ) then GOTO L").p(_opn).nl();
    }
  }
  
  // --------------------------------------------------------
  // 0x8F JMP_VAL_N
  public static class JMP_Val_N extends XOp {
    final String _name;         // Expression result name
    final int _narms;           // Number of switch arms
    final TCon[] _matches;      // Constants from method pool
    final int[] _opns;          // XCode opcode numbers for each arm
    final int _def_opn;         // XCode opcode number for default
    final int _nregs;           // Number of registers needed for each arm
    final String[] _regs;       // Register for each test bit
    final long _isSwitch;       // Unused?
    
    JMP_Val_N( XClzBuilder X ) {
      // Generic Op_Switch
      _narms = X.u31();
      _matches = new TCon[_narms];
      _opns = new int[_narms];
      for( int i=0; i<_narms; i++ ) {
        _matches[i] = (TCon)X.methcon(); // Match constants; might be an array of constants
        _opns[i] = X._opn + (int)X.pack64();  // XCode offsets
        X._jmp_to.set(_opns[i]);
      }
      _def_opn = X._opn + (int)X.pack64();
      X._jmp_to.set(_def_opn);
        
      // Specific to JMP_VAL_N
      _nregs = X.u31();
      _regs = new String[_nregs];
      _isSwitch = X.pack64();
      for( int i=0; i<_nregs; i++ )
        _regs[i] = X.rvalue(); // Becomes 4 / 5, guessing local variables, X1, X2
      _name = X.jname(null);
      X._locals.put(-1,_name);      
    }
    @Override void emit( SB sb ) {
      // Code emitted:
      //
      // val expr; // Register -1
      // if( false ) { // nothing
      // } else if( X1.equals(Integer(0)) && X2.equals(Integer(0)) ) {
      //   expr = Code offset 2
      // } else if( X1.equals(Integer(0)) && true ) {
      //   expr = Code offset 5
      // } else if( true && X1.equals(Integer(0)) && true ) {
      //   expr = Code offset 8
      // } else {
      //   expr = Fall-thru
      // }
      sb.ip("val ").p(_name).p(";").nl();
      sb.ip("if( false ) { // Nothing").nl();
      
      // For each arm, emit guard test
      for( int i=0; i<_narms; i++ ) {
        sb.ip("} else if( ");  // Prelude
        TCon mcon = _matches[i];
        Const[] mvals;
        if( mcon instanceof AryCon arycon ) mvals = arycon.cons();
        else throw XEC.TODO();
        boolean more=false;
        for( int j=0; j<_nregs; j++ ) // Per-register test
          more = emit_guard( sb, _regs[j], mvals[j], more );
        sb.p(" ) {").nl();
        // Emit code
        sb.ii().ip("GOTO L").p(_opns[i]).p(";").di().nl();
      }
      sb.ip("} else {").nl();  // Default
      sb.ii().ip("GOTO L").p(_def_opn).p(";").di().nl();
      sb.ip("}").nl(); 
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
    final String _val1, _val2, _name, _jop;
    BinOp( XClzBuilder X, String jop ) {
      _val1 = X.rvalue();
      _val2 = X.rvalue();
      _name  = X.lvalue();
      _jop = jop;
    }
    @Override void emit( SB sb ) {
      sb.ip(_name).p(" = ").p(_val1).p(" ").p(_jop).p(" ").p(_val2).p(";").nl();
    }
  }

  // --------------------------------------------------------
  // 0xAB IP_INC
  public static class IP_INC extends XOp {
    final int _inc;
    final String _dst;
    IP_INC( XClzBuilder X, int inc ) {
      _inc = inc;
      _dst = X.lvalue();
    }
    @Override void emit( SB sb ) {
      sb.ip(_dst).p(" += ").p(_inc).nl();
    }
  }
}
