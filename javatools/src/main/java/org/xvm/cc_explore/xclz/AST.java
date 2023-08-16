package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;

abstract class AST {
  final AST[] _kids;
  AST( XClzBuilder X, int n ) {
    if( n == 0 ) { _kids=null; return; }
    _kids = new AST[n];
    for( int i=0; i<n; i++ )
      _kids[i] = X.ast();
  }
  void jcode(SB sb) {
    jpre(sb);
    if( _kids!=null )
      for( AST ast : _kids )
        ast.jcode(sb);
    jpost(sb);
  }
  abstract void jpre( SB sb );
  abstract void jpost( SB sb );
}

