package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;
import org.xvm.xtc.*;
import org.xvm.util.S;
import org.xvm.util.SB;

public class DefRegAST extends AST {
  final String _name;
  final String _init;
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
    _name = name==null ? "$def"+(X._locals._len) : ClzBuilder.jname(((StringCon)name)._str);

    if( init instanceof AnnotTCon anno ) {
      _init = XValue.val (anno);
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
  public DefRegAST( XType type, String name, String init ) { super(null); _type=type; _name=name; _init=init; }

  @Override String name() { return _name; }
  @Override XType _type() { return _type; }

  @Override void jpre( SB sb ) {
    if( _type!=null ) _type.clz(sb);
    sb.p(" ").p(_name);
    if( _init != null ) sb.p(" = ").p(_init);
  }
}
