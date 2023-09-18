package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.util.Ary;
import java.util.HashMap;

class BlockAST extends AST {
  HashMap<String,Ary<String>> _tmps;
  final int _nlocals;           // Number of locals pre-existing this Block
  
  BlockAST( XClzBuilder X ) {
    super(X, X.u31(), false);
    _nlocals = X._nlocals;
    
    // Parse kids in order as stmts not exprs
    for( int i=0; i<_kids.length; i++ )
      _kids[i] = ast(X);
    
    // Pop scope-locals at end of scope
    X.pop_locals(_nlocals);
  }
  
  String add_tmp(String type) {
    if( _tmps==null ) _tmps = new HashMap<>();
    Ary<String> tmps = _tmps.get(type);
    if( tmps==null ) _tmps.put(type,tmps=new Ary<String>(new String[1],0));
    return tmps.push("$tmp"+tmps._len);
  }
  
  @Override void jpre( SB sb ) {
    sb.p("{").ii().nl();
    // Print tmps used by enclosing expressions
    if( _tmps!=null ) {
      for( String type : _tmps.keySet() ) {
        Ary<String> tmps = _tmps.get(type);
        sb.ip(type).p(" ");
        for( String tmp : tmps )
          sb.p(tmp).p(", ");
        sb.unchar(2).p(";").nl();
      }
    }
  }
  @Override void jmid( SB sb, int i ) { sb.p(";").nl(); }
  @Override void jpost  ( SB sb ) { sb.di().ip("}"); }
}
