package org.xvm.xtc.ast;

import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.XEC;

class TemplateAST extends AST {
  static TemplateAST make( ClzBuilder X) { return new TemplateAST(X.kids()); }
  private TemplateAST( AST[] kids ) {
    super(kids);
    _kids[0]=null;              // Kill the tmp def
  }
  @Override XType _type() { return XType.STRING; }
  
  /* Need a nested expression out of it
     e0.appendTo(NEST) // OBJS
     NEST.appendTo(e0) // PRIMS
   */
  @Override public SB jcode( SB sb ) {
    return nest(_kids.length-1,sb).p(".toString()");
  }

  private SB nest(int i, SB sb) {
    // Innermost nested expression
    if( i==0 ) return sb.p("new StringBuffer()");
    // Primitives and String ... "NEST.appendTo(_kids[i])"
    if( _kids[i]._type.is_jdk() ) {
      _kids[i].jcode(nest(i-1,sb).p(".appendTo(")).p(")");
    } else {
      // XEC objects ... "_kids[i].appendTo(NEST)"
      nest(i-1,_kids[i].jcode(sb).p(".appendTo(")).p(")");
    }
    return sb;
  }
}
