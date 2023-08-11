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
  // 0x11 - CALL_11
  public static class Call extends XOp {
    final String _method;
    final String[] _args, _rets;
    Call( XClzBuilder X, int nargs, int nrets ) {
      MethodCon mcon = (MethodCon)X.methcon();
      _method = mcon.name();
      _args = new String[nargs];
      for( int i=0; i<nargs; i++ )
        _args[i] = X.rvalue();
      _rets = new String[nrets];
      for( int i=0; i<nrets; i++ )
        _rets[i] = X.lvalue();
      if( nrets>1 ) throw XEC.TODO();
      if( nrets==1 ) _dst = _rets[0];
    }
    @Override void emit( XClzBuilder X ) {
      X._sb.i();
      if( _dst!=null )  X._sb.p(_dst).p(" = ");
      X._sb.p(_method).p("(");
      for( String arg : _args )
        X._sb.p(arg).p(", ");
      if( _args.length>0 ) X._sb.unchar(2);
      X._sb.p(");").nl();
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
  // 0x26 - 0x28 - NVOK_10/1/N
  public static class NVOK_1 extends XOp {
    final String _self, _method;
    final String _arg;
    NVOK_1( XClzBuilder X, int nrets ) {
      _self = X.rvalue();
      MethodCon mcon = (MethodCon)X.methcon();
      _method = mcon.name();
      _arg = X.rvalue();
      assert nrets==0 || nrets==1;
      _dst = nrets==1 ? X.lvalue() : null;
    }
    @Override void emit( XClzBuilder X ) {
      X._sb.ip(_self).p(".").p(_method).p("(").p( _arg ).p(");").nl();
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
  // 0x31 - FBind
  public static class FBind extends XOp {
    final String[] _args;
    final MethodPart _meth;
    FBind( XClzBuilder X ) {
      _meth = (MethodPart)((MethodCon)X.methcon()).part();
      int len = X.u31();
      _args = new String[len];
      for( int i=0; i<len; i++ ) {
        int idx = X.u31();
        assert idx==i;
        _args[i] = X.rvalue();
      }      
      _dst = X.lvalue();
    }
    @Override void emit( XClzBuilder X ) { throw XEC.TODO(); }
  }
  
  
  // --------------------------------------------------------
  // 0x3D - NEWG_1
  public static class NEWG extends XOp {
    final String _arg;
    final String _jtype;
    NEWG( XClzBuilder X, int nargs ) {
      MethodCon mcon = (MethodCon)X.methcon();
      assert mcon.name().equals("construct");
      assert nargs == 1;
      _jtype = X.jtype_methcon();
      _arg = X.rvalue();
      _dst = X.lvalue();
    }
    @Override void emit( XClzBuilder X ) { emit_dst(X._sb); }
    @Override void emit2( SB sb ) { sb.p("new ").p(_jtype).p("()"); }
  }
  
  // --------------------------------------------------------
  // 0x3E - NEWG_N
  public static class NEWG_N extends XOp {
    final String[] _args;
    final String _jtype;
    NEWG_N( XClzBuilder X ) {
      MethodCon mcon = (MethodCon)X.methcon();
      assert mcon.name().equals("construct");
      _jtype = X.jtype_methcon();
      int nargs = X.u31();
      _args = new String[nargs];
      for( int i=0; i<nargs; i++ )
        _args[i] = X.rvalue();
      _dst = X.lvalue();
    }
    @Override void emit( XClzBuilder X ) { throw XEC.TODO(); }
  }
  
  // --------------------------------------------------------
  // 0x4C - 0x4E: RETURN_0, RETURN_1, RETURN_N
  public static class Return_N extends XOp {
    final int _n;
    final String _src;
    Return_N( XClzBuilder X, int n ) {
      _n=n;
      if( n == 1 ) _src = X.rvalue();
      else if( n != 0 ) throw XEC.TODO();
      else _src = null;
    }
    @Override void emit( XClzBuilder X ) {
      X._sb.ip("return");
      if( _n==0 ) ;
      else if( _n==1 ) X._sb.s().p(_src);
      else throw XEC.TODO();  // Multi-return
      X._sb.p(";").nl();
    }
  }

  // --------------------------------------------------------
  public abstract static class AVar extends XOp {
    final String _jtype, _name;
    boolean _fold;              // Fold with next op as initializer
    AVar( XClzBuilder X, boolean is_typename ) {
      _jtype = X.jtype_methcon();
      _name  = is_typename ? X.jname(_jtype) : X.jname_methcon();
      X.define(_name);
    }
    // Optimize:
    // "type name; name = init;" to
    // "type name = init;"
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
  // 0x50: VAR
  public static class Var extends AVar {
    Var( XClzBuilder X ) { super(X,true); }
  } 

  // --------------------------------------------------------
  // 0x52: VAR_N
  public static class Var_N extends AVar {
    Var_N( XClzBuilder X ) { super(X,false); }
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
      _jtype = XClzBuilder.jtype_tcon(ttc,false);
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
  abstract static class AJump extends XOp {
    int _opn;                   // Target opcode index
    boolean _exit_loop;         // Forward, and a loop exit; set in pass 2
    boolean _forward_expr;      // Forward, and part of a "trinary"/n-way expr
    boolean _trinary;           // Forward, head of a "trinary" expr
    int[] _opns;                // Set if trinary is true
    
    void parse_target( XClzBuilder X ) {
      int off = (int)X.pack64();
      _opn = X._opn + off;
      if( off < 0 ) X._xops[_opn]._loop = true;
    }
    
    boolean back( int self ) { return self >= _opn; }
    
    @Override void pass2( XClzBuilder X ) {
      int self = X._opn;
      boolean back = back(self);
      _exit_loop = !back && exit_loop(X._xops,self,_opn);
      if( back || _exit_loop || _forward_expr ) return;
      // Conditional forward jumps are either
      //    "if( pred ) S0; else S1;"
      // OR
      //    "$expr = pred ? S0 : S1;"
      
      // Expect:
      //   JumpRel cond,Ldefault
      //   $expr = ...  opns[0]
      //   Jmp Lend
      //   LINE // Optional
      // Ldefault:      opns[1] // == opns[-2]
      //   $expr = ...
      // Lend:          opns[2] // == opns[-1]
      XOp prior = X._xops[_opn-1];
      if( prior instanceof Line ) prior = X._xops[_opn-1];
      if( prior instanceof Jump jmp ) {
        _opns = new int[]{self+1,_opn,jmp._opn};
        _trinary = trinary(X);
        if( _trinary ) {
          assert _dst==null;
          _dst = "$eval";
        }
      }
    }

    // Check for valid "trinary" format.  If so, set up the assignments.
    // opns[0..] = opcode index for nth branch target
    // opsn[-2 ] = opcode index for default
    // opsn[-1 ] = opcode index for the continuation point
    boolean trinary( XClzBuilder X ) {
      XOp[] xops = X._xops;
      String dst = _dst==null ? "$expr" : _dst;

      // All branch targets are in order
      int prior = X._opn;
      for( int i=0; i<_opns.length-1; i++ ) {
        if( prior >= _opns[i] )
          return false;
        prior = _opns[i];
      }

      // All arms jump to end, except the default falls into end.
      int end_opn = _opns[_opns.length-1];
      for( int arm=0; arm<_opns.length-2; arm++ ) {
        // Prior arm:                      arm
        //   ....
        //   dst = expr        : xops[_opns[arm+1] -3]
        //   jmp End           : xops[_opns[arm+1] -2]
        //   [Line, optional]  : xops[_opns[arm+1] -1]
        // Next arm:           :           arm+1        
        int idx = _opns[arm+1]-1;
        if( xops[idx] instanceof Line ) idx--;
        if( !(xops[idx] instanceof Jump jmp) ) return false;
        if( end_opn != jmp._opn ) return false;
        // Prior assigns to dst
        String xdst = xops[idx-1]._dst;
        if( !dst.equals(xdst) ) return false;
      }
      // Also test default, but the layout is a little different
      XOp rez = xops[end_opn-1];
      if( !dst.equals(rez._dst) ) return false;
      rez._dst = null;

      // All embedded forward jumps to end are "forward_expr".
      // All assignments to _dst become null.
      for( int arm=0; arm<_opns.length-2; arm++ ) {
        int idx = _opns[arm+1]-1;
        if( xops[idx] instanceof Line ) idx--;
        ((AJump)xops[idx])._forward_expr = true;
        xops[idx-1]._dst = null;
      }
      return true;
    }
    
    static boolean exit_loop( XOp[] xops, int self, int target ) {
      assert self < target ;
      for( int x = self+1; x<target; x++ )
        if( xops[x] instanceof AJump jmp && jmp.back(x) )
          return true;
      return false;
    }
  }
  
  // --------------------------------------------------------
  // 0x79 Jump
  public static class Jump extends AJump {
    Jump( XClzBuilder X ) { parse_target(X); }
    @Override void emit( XClzBuilder X ) {
      if( back(X._opn) ) X._sb.di().ip("}").nl();
      else if( _exit_loop ) throw XEC.TODO();
      else X._sb.ip("GOTO ").p("L").p(_opn).p(";").nl();
    }
  }
  
  
  // --------------------------------------------------------
  // 0x7A JMP_TRUE
  // 0x82 JMP_LT
  // 0x84 JMP_GT
  // 0x85 JMP_GTE
  public static class JumpRel extends AJump {
    final String _lhs, _op, _rhs;
    JumpRel( XClzBuilder X, String op ) {
      _lhs = X.rvalue();
      _op = op;
      _rhs = op==null ? null : X.rvalue();
      if( op!=null )  X.jtype_methcon(); // Compare type, ignored
      parse_target(X);
    }
    @Override void emit( XClzBuilder X ) {
      assert !back(X._opn);
      assert !_forward_expr;    // Covered by expr head
      // _exit_loop takes a forward branch to break.  others do "if( pred )
      // then else" and want to invert the test.
      SB sb = X._sb;
      if( _exit_loop ) {
        // if( pred ) break;\n
        tst(sb.ip("if( ")).p(" ) break;").nl();
      } else if( _trinary ) {
        // _dst = pred ? true : false;\n
        assert _dst!=null;
        sb.ip(_dst).p(" = ");
        tst(sb).p(" ? ");
        // Emit code, skipping trailing Line, GOTO
        X.emit(_opns[0],_opns[1]-1);
        sb.p(" : ");
        X.emit(_opns[1],_opns[2]);
        sb.p(";").nl();
        X._opn--; // Adjust for following post-inc, start emitting at the END again
      } else {
        // if( pred ) {\n
        tst(sb.ip("if( !(")).p(") ) {").nl().ii();
        X.emit(X._opn+1,_opn);
        sb.di().ip("}");
        if( X._xops[X._opn-1] instanceof AJump jmp ) {
          sb.p(" else {").ii().nl();
          // TODO: 
          sb.di().ip("}").nl();
          throw XEC.TODO();
        } else {
          sb.nl();
        }
        
      }
    }
    private SB tst( SB sb ) {
      sb.p(_lhs);
      if( _op!=null )  sb.s().p(_op).s().p(_rhs);
      return sb;
    }
  }
  
  // --------------------------------------------------------
  // 0x8F JMP_VAL_N
  public static class JMP_Val_N extends AJump {
    final int _narms;           // Number of switch arms
    final TCon[] _matches;      // Constants from method pool
    final int _nregs;           // Number of registers needed for each arm
    final String[] _regs;       // Register for each test bit
    final long _isSwitch;       // Unused?
    
    JMP_Val_N( XClzBuilder X ) {
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
      _dst = "$expr";  // define special "local result"
    }

    // Confirm nicely laid out and reverse to an AST.
    @Override void pass2( XClzBuilder X ) {
      // Expect:
      //   Jump_Val_N L0,L1,...,Ldefault
      //   LINE
      // L0:
      //   $expr = ...   _opns[0]
      //   Jmp Lend
      //   LINE
      // L1:             _opns[1]
      //   $expr = ...
      //   Jmp Lend
      //   LINE
      // Ldefault:       _opns[-2]
      //   $expr = ...
      // Lend:           _opns[-1]      
      _opns[_narms+1] = ((Jump)X._xops[_opns[1]-2])._opn;
      boolean tri = trinary(X);
      assert tri;
    }
    
    @Override void emit( XClzBuilder X ) {
      /*
       var expr =
         (x1==0 && x2==0) ? "FizzBuzz" :
         (x1==0) ? "Buzz" :
         (x2==0) ? "Fizz" :
         default();
      */
      X._sb.ip("var ").p(_dst).p(" = ").ii().nl();
      
      // For each arm, emit guard test
      for( int i=0; i<_narms; i++ ) {
        X._sb.ip("(");             // Prelude
        TCon mcon = _matches[i];
        Const[] mvals;
        if( mcon instanceof AryCon arycon ) mvals = arycon.cons();
        else throw XEC.TODO();
        boolean more=false;
        for( int j=0; j<_nregs; j++ ) // Per-register test
          more = emit_guard( X, _regs[j], mvals[j], more );
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
    
    static private boolean emit_guard( XClzBuilder X, String reg, Const match, boolean more ) {
      if( match instanceof MatchAnyCon ) return more; // Skip "any" matches
      if( more ) X._sb.p(" && ");                     // Concatenate tests
      X._sb.p(reg).p(" == ").p(X.value_tcon((TCon)match));
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
  // 0xA1 RANGE
  public static class Range extends XOp {
    final String _lo, _hi; // Lo inclusive, Hi exclusive
    final int _i0,_i1;
    Range( XClzBuilder X, int i0, int i1 ) {
      _lo = X.rvalue();
      _hi = X.rvalue();
      _i0 = i0;
      _i1 = i1;
      _dst = X.lvalue();
    }
    @Override void emit( XClzBuilder X ) { emit_dst(X._sb); }
    @Override void emit2( SB sb ) {
      sb.p("new XRange(");
      adj(sb.p(_lo),_i0).p(",");
      adj(sb.p(_hi),_i1).p(")");
    }
    private static SB adj( SB sb, int off ) {
      return sb.p(switch( off ) {
        case 1 -> "+1";
        case 0 -> "";
        case -1 -> "-1";
        default -> throw XEC.TODO();
        });
    }
  }
  
  // --------------------------------------------------------
  // 0xA7 P_GET
  public static class P_Get extends XOp {
    final String _prop, _lhs;
    P_Get( XClzBuilder X ) {
      PropCon prop = (PropCon)X.methcon();
      _prop = prop.name();
      _lhs = X.rvalue();
      _dst = X.rvalue();      
    }
    @Override void emit( XClzBuilder X ) { emit_dst(X._sb); }
    @Override void emit2( SB sb ) { sb.p(_lhs).p(".").p(_prop).p("()");  }
  }
  
  // --------------------------------------------------------
  // 0xAB IP_INC
  // 0xAC IP_DEC
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

  // --------------------------------------------------------
  // 0xCD I_GET
  public static class I_Get extends XOp {
    final String _ary, _idx;
    I_Get( XClzBuilder X ) {
      _ary = X.rvalue();
      _idx = X.rvalue();
      _dst = X.lvalue();
    }
    @Override void emit( XClzBuilder X ) { emit_dst(X._sb); }
    @Override void emit2( SB sb ) { sb.p(_ary).p(".get(").p(_idx).p(")"); }
  }
  

}
