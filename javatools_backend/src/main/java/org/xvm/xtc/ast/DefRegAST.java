package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;
import org.xvm.xtc.*;
import org.xvm.util.S;
import org.xvm.util.SB;

class DefRegAST extends AST {
  final String _name;
  String _init;
  int _reg;                     // Register number

  static DefRegAST make( ClzBuilder X, boolean named, boolean initd ) {
    Const init = initd ? X.con() : null;
    Const type =         X.con()       ;
    Const name = named ? X.con() : null;
    return new DefRegAST(X,init,type,name);
  }
  private DefRegAST( ClzBuilder X, Const init, Const type, Const name ) {
    super(null);
    
    // Destination is read first and is type-aware, so read the destination type.
    _type = XType.xtype(type,false);
    if( _type instanceof XClz clz )
      ClzBuilder.add_import(clz);
    if( name==null ) _name = null;
    else {
      String s = ((StringCon)name)._str;
      _name = S.eq(s,"_") ? "$ignore" : s;
    }
    
    if( init instanceof AnnotTCon anno ) {
      _init = XValue.val (X,anno);
      _type = XType.xtype(anno,true);
      
    } else if( init != null ) {
      throw XEC.TODO();
    } else {
      _init = null;
    }

    // At least FutureVar redefines the type so save the AST architected type
    // till after annotation processing
    _reg = X.define(_name,_type);
  }
  DefRegAST( XType type, String name, String init ) { super(null); _type=type; _name=name; _init=init; }
  
  @Override String name() { return _name; }
  @Override XType _type() { return _type; }
  
  @Override void jpre( SB sb ) {
    _type.clz(sb).p(" ").p(_name);
    if( _init != null ) sb.p(" = ").p(_init);
  }
}
