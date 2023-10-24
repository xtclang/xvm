package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.xrun.Range;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;

// Print as a switch expression.
class SwitchAST extends AST {
  //  {
  //    int $tmp0, $tmp1; // Temps in enclosing block
  //    console.print(    // Intervening complex code
  //      $t($tmp0 = x%3) & $t($tmp1 = x%5) & // Fill the temps, computing side effects once
  //      $tmp0==3 && $tmp1==5 ? "FizzBuzz" :
  //      $tmp0==3 ? "Fizz" :
  //      $tmp1==5 ? "Buzz" :
  //      x.toString()
  //                  );

  // Switch is an expression vs statement
  final boolean _expr;
  
  // 64 bits mask for isa tests.  Beyond 64 requires a different presentation
  final long _isa;
  // Case values; these in turn might be tuples of values with different types
  final Const[] _cases;
  // Match parts for each case
  final String[][] _arms;
  // Temps for the case parts
  String[] _tmps;
  // Result types
  Const[] _rezs;

  static SwitchAST make( XClzBuilder X, boolean expr ) {
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
        if( (body_mask & (1L << i)) != 0 ) // Skip some cases
          kids[i+1] = expr ? ast_term(X) : ast(X);
    } else {                    // Use another encoding
      throw XEC.TODO();
    }

    // Confirm either a multi-arm pattern match, OR
    // a selection of ints and int ranges
    if( cond instanceof MultiAST ) {
      // this is a multi-arm pattern match
    } else if( "long".equals(cond._type) ) {
      // This is single-ints, confirm all case arms are ints (or ranges)
      for( Const c : cases )
        if( !valid_range( c ) )
          throw XEC.TODO();
    } else {
      // This is a series of singleton matches, e.g. Strings or Enums
      if( valid_range( cases[0] ) ) throw XEC.TODO();
    }
    
    // Parse result types
    Const[] rezs = expr ? X.consts() : null;
    return new SwitchAST(kids,expr,isa,cases,rezs);
  }
  private static boolean valid_range( Const c ) {
    return c == null || c instanceof IntCon || (c instanceof RangeCon rcon && rcon._lo instanceof IntCon);
  }
  
  private SwitchAST( AST[] kids, boolean expr, long isa, Const[] cases, Const[] rezs ) {
    super(kids);
    _expr = expr;
    _isa = isa;
    _cases = cases;
    _rezs = rezs;

    // Pre-cook the match parts.    
    // Each pattern match has the same count of arms
    int clen = cases.length;
    int alen;
    if( cases[0] instanceof AryCon ary0 ) {
      assert ary0.type() instanceof ImmutTCon;
      alen = ary0.cons().length;
      _arms = new String[clen][alen];
      for( int i=0; i<clen-1; i++ ) {
        Const[] cons = ((AryCon)cases[i]).cons();
        for( int j=0; j<alen; j++ )
          _arms[i][j] = cons[j] !=null ? XClzBuilder.value_tcon(cons[j]) : null;
      }

    } else if( valid_range( cases[0] ) ) {      
      // Here each arm has 1 part, an exact integer check, or a constant range check
      _arms = new String[clen][1];
      for( int i=0, j=0; i<clen-1; i++, j++ ) {
        if( _cases[j] instanceof RangeCon rcon ) {
          long lo = Range.lo(rcon); // Insert the range now
          long hi = Range.hi(rcon);
          SB sb = new SB();
          for( long k=lo; k<hi; k++ )
            sb.p(k).p(", ");
          _arms[i][0] = sb.unchar(2).toString();
        } else {
          _arms[i][0] = ""+((IntCon)_cases[j])._x;
        }
      }
      
    } else {
      // This is a series of singleton matches, e.g. Strings or Enums.
      _arms = new String[clen][1];
      for( int i=0; i<clen; i++ )
        // This might be an exact check, or might have a default.
        if( _cases[i] != null ) {
          String arm = XClzBuilder.value_tcon(_cases[i]);
          // Enum arms must be the unqualified enum name.
          int idx = arm.lastIndexOf(".");
          if( idx >=0 ) arm = arm.substring(idx + 1);
          _arms[i][0] = arm;
        }
    }
  }

  @Override boolean is_loopswitch() { return true; }
  
  @Override String _type() { return "void"; }
  
  // Pre-cook the temps
  @Override AST rewrite() {
    BlockAST blk = enclosing_block();
    if( _kids[0] instanceof MultiAST cond ) {
      _tmps = new String[cond._kids.length];
      for( int i=0; i<cond._kids.length; i++ )
        _tmps[i] = blk.add_tmp(cond._kids[i]._type);
    }
    return this;
  }
  
  @Override SB jcode( SB sb ) {
    String case_sep = _expr ? " -> " : ": ";
    if( sb.was_nl() ) sb.i();
    if( _kids[0] instanceof MultiAST cond ) {
      for( int i=0; i<_tmps.length; i++ ) {
        sb.p(" $t(").p(_tmps[i]).p("= ");
        cond._kids[i].jcode(sb);
        sb.p(") &");
      }
      sb.nl().ii();

      // for each case label
      for( int i=0; i<_arms.length-1; i++ ) {
        sb.i();
        // for each arm
        for( int j=0; j<_tmps.length; j++ ) {
          String arm = _arms[i][j];
          if( arm != null ) {   // Null arms are MatchAny and do not encode a test
            // Arms with an allocation "new Range()" call "in"
            if( arm.charAt(arm.length()-1)==')' )  sb.p(arm).p(".in(").p(_tmps[j]).p(")");
            else                                   sb.p(_tmps[j]).p("==").p(arm);
            sb.p(" && ");
          }
        }
        sb.unchar(3).p("? ");
        _kids[i+1].jcode(sb);
        sb.p(" :").nl();
      }
      sb.i();                     // The default case
      _kids[_arms.length].jcode(sb);
      sb.di();                 // And the expression continues on the next line
      
    } else if( valid_range(_cases[0]) ) {
      // Single integer test case
      sb.p("switch( (int)");
      _kids[0].jcode(sb);
      sb.p(" ) {").nl().ii();
      // for each case label
      for( int i=0; i<_arms.length; ) {
        if( i < _arms.length-1 ) {
          sb.ip("case ");
          do sb.p( _arms[i][0] ).p( ", " );
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
      
    } else {
      // This is a series of singleton matches, e.g. Strings or Enums
      sb.p("switch( ");
      _kids[0].jcode(sb);
      sb.p(" ) {").nl().ii();
      
      for( int i=0; i<_arms.length; i++ ) {
        if( _arms[i][0]==null ) sb.p("default"); else sb.ip("case ").p(_arms[i][0]);
        sb.p(case_sep);
        _kids[i+1].jcode(sb);
        if( !(_kids[i+1] instanceof BlockAST) ) sb.p(";");
        sb.nl();
      }
      sb.di().ip("}");
    }
    
    return sb;
  }
}
