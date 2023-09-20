package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
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
  
  // 64 bits mask for isa tests.  Beyond 64 requires a different presentation
  final long _isa;
  // Case values; these in turn might be tuples of values with different types
  final Const[] _cases;
  // Match parts for each case
  final TCon[][] _arms;
  // Temps for the case parts
  String[] _tmps;
  // Result types
  Const[] _rezs;

  static SwitchAST make( XClzBuilder X ) {
    AST cond = ast_term(X);
    long isa = X.pack64();
    Const[] cases = X.consts();
    int clen = cases.length;
    AST[] kids = new AST[clen+1];
    // Condition
    kids[0] = cond;
    if( !(cond instanceof MultiAST) )
      throw XEC.TODO();
    // Parse bodies; less than 64 uses isa bitvector
    if( clen < 64 ) {   // Use bitvector
      long body_mask = X.pack64();
      for( int i = 0; i < clen;  i++ )
        kids[i+1] = (body_mask & (1L << i)) == 0 ? null : ast_term(X);
    } else {                    // Use another encoding
      throw XEC.TODO();
    }
    // Parse result types
    Const[] rezs = X.consts();
    return new SwitchAST(kids,isa,cases,rezs);
  }
  
  private SwitchAST( AST[] kids, long isa, Const[] cases, Const[] rezs ) {
    super(kids);
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
      _arms = new TCon[clen][alen];
      assert cases[clen-1]==null; // The default case
      for( int i=0; i<clen-1; i++ ) {
        AryCon ary = (AryCon)cases[i];
        for( int j=0; j<alen; j++ )
          _arms[i][j] = ary.cons()[j] instanceof TCon tc0 ? tc0 : null;
      }      
    } else {
      throw XEC.TODO();
    }
  }

  // Pre-cook the temps
  @Override AST rewrite() {
    BlockAST blk = enclosing();
    MultiAST cond = (MultiAST)_kids[0];
    _tmps = new String[cond._kids.length];
    for( int i=0; i<cond._kids.length; i++ )
      _tmps[i] = blk.add_tmp(cond._kids[i].type());    
    return this;
  }
  
  @Override SB jcode( SB sb ) {
    if( sb.was_nl() ) sb.i();
    MultiAST cond = (MultiAST)_kids[0];
    for( int i=0; i<_tmps.length; i++ ) {
      sb.p(" $t(").p(_tmps[i]).p("= ");
      cond._kids[i].jcode(sb);
      sb.p(") &");
    }
    sb.nl().ii();

    // for each case label
    for( int i=0; i<_cases.length-1; i++ ) {
      sb.i();
      // for each arm
      for( int j=0; j<_tmps.length; j++ )
        if( _arms[i][j] != null )
          sb.p(_tmps[j]).p("==").p(XClzBuilder.value_tcon(_arms[i][j])).p(" && ");
      sb.unchar(3).p("? ");
      _kids[i+1].jcode(sb);
      sb.p(" :").nl();
    }
    sb.i();                     // The default case
    _kids[_cases.length].jcode(sb);
    return sb.di().nl().i();    // And the expression continues on the next line
  }
}
