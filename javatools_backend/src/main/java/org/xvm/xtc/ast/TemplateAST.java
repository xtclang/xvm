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
  @Override XType _type() { return XCons.STRING; }

  @Override public SB jcode( SB sb ) {
    sb.p("new StringBuffer()");
    for( int i=1; i<_kids.length; i++ ) {
      _kids[i].jcode(sb.p(".appendTo("));
      if( !_kids[i]._type.is_jdk() )
        sb.p(".toString()");
      sb.p(")");
    }
    return sb.p(".toString()");
  }

}
