package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xec.ecstasy.AbstractRange;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.*;
import org.xvm.util.SB;

// Print as a switch expression.
class SwitchAST extends AST {

  // Switch is an expression vs statement
  final boolean _expr;

  // Conditional created new vars which need hoisting to a block:
  // "switch( Char ch = str.charAt(i) ) { ..."
  boolean _cond_defines;

  // 64 bits mask for isa tests.  Beyond 64 requires a different presentation
  final long _isa;
  // Case values; these in turn might be tuples of values with different types
  final Const[] _cases;
  // Match parts for each case
  final String[][] _armss;
  // Temps for the case parts
  String[] _tmps;
  // Result types
  Const[] _rezs;

  // How we encode this switch in Java
  enum Flavor { ComplexTern, SimpleTern, IntRange, Pattern };
  final Flavor _flavor;

  static SwitchAST make( ClzBuilder X, boolean expr ) {
    int nlocals = X.nlocals();  // Count of locals
    Flavor flavor;
    AST cond = ast_term(X);
    long isa = X.pack64();
    Const[] cases = X.consts();
    int clen = cases.length;
    AST[] kids = new AST[clen+1];
    // Condition
    kids[0] = cond;
    // Parse bodies; less than 64 uses isa bitvector
    if( clen < 64 ) {   // Use bitvector
      long body_mask = X.pack64();
      for( int i = 0; i < clen;  i++ )
        if( (body_mask & (1L << i)) != 0 ) { // Skip some cases
          int body_locals = X.nlocals();  // Count of locals
          kids[i+1] = expr ? ast_term(X) : ast(X);
          X.pop_locals(body_locals); // Pop scope-locals at end of scope
        }
    } else {                    // Use another encoding
      throw XEC.TODO();
    }

    // Confirm either a multi-arm pattern match, OR
    // a selection of ints and int ranges
    if( cond instanceof MultiAST ) {
      flavor = Flavor.ComplexTern;  // this is a multi-arm pattern match
    } else if( cases[0] instanceof EnumCon ) {
      flavor = Flavor.Pattern; // This is a series of singleton matches, e.g. Strings or Enums
    } else if( cases[0] instanceof SingleCon ) {
      flavor = Flavor.SimpleTern;
    } else {
      flavor = Flavor.IntRange;
      // This is single-ints, confirm all case arms are int-like (or ranges)
      for( Const c : cases )
        if( !valid_range( c ) )
          throw XEC.TODO();
    }

    // Parse result types
    Const[] rezs = expr ? X.consts() : null;
    boolean cond_defines = nlocals != X.nlocals();
    X.pop_locals(nlocals);      // Pop scope-locals at end of scope
    return new SwitchAST(X,flavor,kids,expr,isa,cases,rezs, cond_defines);
  }
  private static boolean valid_range( Const c ) {
    return c == null || c instanceof IntCon || c instanceof CharCon || (c instanceof RangeCon rcon && valid_range(rcon._lo));
  }

  private SwitchAST( ClzBuilder X, Flavor flavor, AST[] kids, boolean expr, long isa, Const[] cases, Const[] rezs, boolean cond_defines ) {
    super(kids);
    _flavor = flavor;
    _expr = expr;
    _isa = isa;
    _cases = cases;
    _rezs = rezs;
    _cond_defines = cond_defines;

    // Pre-cook the match parts.
    // Each pattern match has the same count of arms
    int clen = cases.length;
    _armss = new String[clen][];
    switch(flavor) {
    case ComplexTern: // Complex multi-part arms
      int alen = ((AryCon)cases[0]).cons().length;
      for( int i=0; i<clen-1; i++ ) {
        String[] arms = _armss[i] = new String[alen];
        Const[] cons = ((AryCon)cases[i]).cons();
        for( int j=0; j<alen; j++ )
          arms[j] = cons[j] !=null ? XValue.val(X,cons[j]) : null;
      }
      break;

    case IntRange: // Here each arm has 1 part, an exact integer check, or a constant range check
      for( int i=0; i<clen-1; i++ ) {
        String arm;
        if( _cases[i] instanceof RangeCon rcon ) {
          long lo = AbstractRange.start(rcon); // Insert the range now
          long hi = AbstractRange.end  (rcon);
          SB sb = new SB();
          for( long k=lo; k<hi; k++ )
            sb.p(k).p(", ");
          arm = sb.unchar(2).toString();
        } else {
          arm = ""+((NumCon)_cases[i])._x;
        }
        _armss[i] = new String[]{arm};
      }
      break;

    case Pattern: // This is a series of singleton matches, e.g. Strings or Enums.
      for( int i=0; i<clen; i++ )
        // This might be an exact check, or might have a default.
        if( _cases[i] != null ) {
          String arm = XValue.val(X,_cases[i]);
          // Enum arms must be the unqualified enum name.
          int idx = arm.lastIndexOf(".");
          if( idx >=0 ) arm = arm.substring(idx + 1);
          _armss[i] = new String[]{arm};
        }
      break;

    case SimpleTern:
      // A list of singleton constants
      for( int i=0; i<clen-1; i++ )
        _armss[i] = new String[]{XValue.val(X,cases[i])};
      break;

    }
  }

  @Override boolean is_loopswitch() { return true; }

  @Override XType _type() { return XCons.VOID; }

  // Pre-cook the temps
  @Override AST prewrite() {
    BlockAST blk = enclosing_block();
    switch( _flavor ) {
    case ComplexTern:
      AST[] kids = _kids[0]._kids;
      _tmps = new String[kids.length];
      for( int i=0; i<kids.length; i++ )
        _tmps[i] = blk.add_tmp(kids[i]._type);
      break;
    case IntRange:
      assert _kids[0]._type instanceof XBase && _kids[0]._type != XCons.STRING;
      _tmps=null; break; // No temps
    case Pattern: _tmps=null; break; // No temps
    case SimpleTern:
      _tmps = new String[]{blk.add_tmp(_kids[0]._type)};
      break;
    }

    if( _cond_defines )
      hoist_defs(_kids[0],blk);

    return this;
  }

  // Hoist any DefRegAST into the Block
  static void hoist_defs(AST ast, BlockAST blk) {
    if( ast._kids == null ) return;
    for( int i=0; i<ast._kids.length; i++ ) {
      AST kid = ast._kids[i];
      if( kid instanceof DefRegAST def ) {
        ast._kids[i] = new RegAST(-1,def._name,def._type);
        blk.add_tmp(def._type,def._name);
      } else if( kid != null )
        hoist_defs(kid,blk);
    }
  }


  @Override public SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();

    return switch( _flavor ) {
      case ComplexTern -> do_tern( sb, _kids[0] );
      case IntRange    -> do_range( sb );
      case Pattern     -> do_pat_switch( sb );
      case SimpleTern  -> do_simple_tern( sb );
    };
  }

  // Series of complex tests as a ternary tree.
  //  {
  //    int $tmp0, $tmp1; // Temps in enclosing block
  //    console.print(    // Intervening complex code
  //      $t($tmp0 = x%3) & $t($tmp1 = x%5) & // Fill the temps, computing side effects once
  //      $tmp0==3 && $tmp1==5 ? "FizzBuzz" :
  //      $tmp0==3 ? "Fizz" :
  //      $tmp1==5 ? "Buzz" :
  //      x.toString()
  //                  );
  private SB do_tern( SB sb, AST cond ) {
    for( int i=0; i<_tmps.length; i++ ) {
      sb.p(" $t(").p(_tmps[i]).p("= ");
      cond._kids[i].jcode(sb);
      sb.p(") &");
    }
    sb.nl().ii();

    // for each case label
    for( int i=0; i<_armss.length-1; i++ ) {
      sb.i();
      // for each arm
      for( int j=0; j<_tmps.length; j++ ) {
        String arm = _armss[i][j];
        if( arm != null ) {   // Null arms are MatchAny and do not encode a test
          // Arms with an allocation "new Range()" call "in"
          if( arm.charAt(arm.length()-1)==')' )  sb.p(arm).p(".in(").p(_tmps[j]).p(")");
          else                                   sb.p(_tmps[j]).p("==").p(arm);
          sb.p(" && ");
        }
      }
      sb.unchar(3).p("? ");
      asExpr(_kids[i+1],sb).p(" :").nl();
    }
    sb.i();                     // The default case
    return asExpr(_kids[_armss.length],sb).di(); // And the expression continues on the next line
  }

  // Encode kid as expr, otherwise it is a statement
  private static SB asExpr(AST kid, SB sb) {
    if( kid._type != XCons.VOID )
      return kid.jcode(sb);
    ClzBuilder.add_import("java.util.function.LongSupplier");

    // Evil Java Code:
    // Makes a Lambda supplier, casts to it (required for valid Java parsing)
    // then calls it with no args to get the result result.  Meanwhile the
    // lambda body can have e.g. side-effects, or throw clauses, etc.
    // TODO: do other types than Longs.
    sb.p("((LongSupplier) () -> {");
    kid.jcode(sb);
    sb.p(";}).getAsLong()");
    return sb;
  }


  // Series of exact tests
  // $t($tmp=e0) &
  // $tmp==con0 ? arm0 :
  // $tmp==con1 ? arm1 :
  // def
  private SB do_simple_tern( SB sb ) {
    XType xt = _kids[0]._type;
    sb.p(" $t(").p(_tmps==null ? null : _tmps[0]).p("= ");
    _kids[0].jcode(sb);
    sb.p(") &");
    sb.nl().ii();
    // For each arm
    for( int i=0; i<_armss.length-1; i++ ) {
      xt.do_eq(sb.ip(_tmps==null ? null : _tmps[0]),_armss[i][0]).p(" ? ");
      _kids[i+1].jcode(sb);
      sb.p(" :").nl();
    }
    sb.i();                     // The default case
    _kids[_armss.length].jcode(sb);
    sb.di();
    return sb;
  }

  // Single integer test case
  // Emits as a Java switch:
  // switch( int ) {
  // case 1,2,3 -> arm;
  // case 4,5,6 -> arm;
  // default -> def;
  // }
  private SB do_range( SB sb ) {
    String case_sep = _expr ? " -> " : ": ";
    sb.p("switch( (int)(");
    _kids[0].jcode(sb);
    sb.p(") ) {").nl().ii();
    // for each case label
    for( int i=0; i<_armss.length; ) {
      if( i < _armss.length-1 ) {
        sb.ip("case ");
        do sb.p( _armss[i][0] ).p( ", " );
        while( _kids[++i] == null );
      } else {
        sb.ip("default, ");
        i++;
      }
      sb.unchar(2).p(case_sep);
      _kids[i].jcode(sb);
      if( !(_kids[i] instanceof BlockAST) ) sb.p(";");
      sb.nl();
    }
    sb.di().ip("}");
    return sb;
  }

  // This is a series of singleton matches, e.g. Strings or Enums.
  // Emits as a Java switch:
  // switch( e0 ) {
  // case "abc" -> arm;
  // case "def" -> arm;
  // default -> def;
  // }
  private SB do_pat_switch( SB sb ) {
    String case_sep = _expr ? " -> " : ": ";
    sb.p("switch( ");
    _kids[0].jcode(sb);
    sb.p(" ) {").nl().ii();

    for( int i=0; i<_armss.length; i++ ) {
      if( _armss[i][0]==null ) sb.ip("default"); else sb.ip("case ").p(_armss[i][0]);
      sb.p(case_sep);
      _kids[i+1].jcode(sb);
      if( !(_kids[i+1] instanceof BlockAST) ) sb.p(";");
      sb.nl();
    }
    sb.di().ip("}");
    return sb;
  }

}
