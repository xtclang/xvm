package org.xvm.xclz;

import org.xvm.util.SB;
import org.xvm.util.Ary;
import java.util.HashMap;

public class BlockAST extends AST {
  HashMap<XType,Ary<String>> _tmps;
  private int _uid;
  
  static BlockAST make( XClzBuilder X ) {
    // Count of locals
    int nlocals = X._nlocals;
    
    // Parse kids in order as stmts not exprs
    AST[] kids = new AST[X.u31()];
    for( int i=0; i<kids.length; i++ )
      kids[i] = ast(X);    
    // Pop scope-locals at end of scope
    X.pop_locals(nlocals);    
    return new BlockAST(kids);
  }
  
  BlockAST( AST... kids ) { super(kids); }

  @Override XType _type() { return XType.VOID; }
  
  String add_tmp(XType type) {
    return add_tmp(type,"$tmp"+_uid++);
  }
  String add_tmp(XType type, String name) {
    assert type != null;
    if( _tmps==null ) _tmps = new HashMap<>();
    Ary<String> tmps = _tmps.get(type);
    if( tmps==null ) _tmps.put(type,tmps=new Ary<String>(new String[1],0));
    return tmps.push(name);
  }

  @Override void jpre( SB sb ) {
    sb.p("{").ii().nl();
    // Print tmps used by enclosing expressions
    if( _tmps!=null ) {
      for( XType type : _tmps.keySet() ) {
        Ary<String> tmps = _tmps.get(type);
        type.str(sb.i()).p(" ");
        for( String tmp : tmps )
          sb.p(tmp).p(", ");
        sb.unchar(2).p(";").nl();
      }
    }
  }
  @Override void jmid( SB sb, int i ) { sb.p(";").nl(); }
  @Override void jpost  ( SB sb ) { sb.di().ip("}"); }
}
