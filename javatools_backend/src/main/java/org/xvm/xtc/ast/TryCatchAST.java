package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;

class TryCatchAST extends AST {

  // kids[0..xbody]      // Resources to close from 0 to xbody
  // kids[xbody]         // the body
  // kids[xbody+1...len] // catch clauses, each a StmtBody with DefReg first
  final int _xbody;
  
  static TryCatchAST make( ClzBuilder X ) {
    AST[] resources = X.kids(); // Resources to close
    int xbody = resources==null ? 0 : resources.length;
    AST body = ast(X);          // Main body
    int ncs = X.u31();          // Number of catch clauses
    AST[] kids = new AST[xbody+1+ncs];
    kids[xbody] = body;
    for( int i=0; i<ncs; i++ )  // Catch clauses
      kids[xbody+1+i] = ast(X); // Each expected to be a BlockAST with a DefReg first
    return new TryCatchAST(kids,xbody);
  }
  private TryCatchAST( AST[] kids, int xbody ) {
    super(kids);
    _xbody = xbody;

    // Each catch is expected to be a BlockAST with a DefReg first, and a
    // single AST or a MultiAST.
    for( int i=xbody+1; i<_kids.length; i++ ) {// Catch clauses
      AST ast = kids[i];
      AST[] aks = ast._kids;
      if( !(ast instanceof BlockAST) || aks==null ||
          aks.length!=2 || !(aks[0] instanceof DefRegAST) ||
          (aks[1] instanceof DefRegAST) )
        throw XEC.TODO();       // Expect a single DefReg for the catch variable
      // Restructure as (Block DefReg  AST     ) as (Block DefReg (Block AST)) 
      // Restructure as (Block DefReg (Multi...) as (Block DefReg (Block ...))
      if( aks[1] instanceof MultiAST  )
        aks[1] = new BlockAST(aks[1]._kids);
      else throw XEC.TODO();
    }
  }

  @Override XType _type() { return XType.VOID; }
  
  @Override public SB jcode( SB sb ) {
    if( _xbody!=0 )
      throw XEC.TODO();         // Need to handle closes
    sb.ip("try {").nl().ii();
    _kids[_xbody].jcode(sb);
    // 1-line body:            // block-body
    //                         try {
    // try foo;                  foo;
    //                         }
    if( !sb.was_nl() ) sb.p(";");
    sb.di().nl().ip("}").nl();
    
    // Catch clauses
    for( int i=_xbody+1; i<_kids.length; i++ ) {
      sb.ip("catch( ");
      BlockAST blk = (BlockAST)_kids[i];
      DefRegAST ex = (DefRegAST)blk._kids[0];
      ex._type.clz(sb).p(" ").p(ex.name()).p(" ) ");
      blk._kids[1].jcode(sb);
    }
    return sb;
  }
}
