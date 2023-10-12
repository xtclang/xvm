package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;

class DefRegAST extends AST {
  final String _type, _name, _init;

  static DefRegAST make( XClzBuilder X, boolean named, boolean initd ) {
    Const init = initd ? X.con() : null;
    Const type =         X.con()       ;
    Const name = named ? X.con() : null;
    return new DefRegAST(X,init,type,name);
  }
  private DefRegAST( XClzBuilder X, Const init, Const type, Const name ) {
    super(null);
    if( init!=null ) {
      // Destination is read first and is typeaware, so read the destination type.
      AnnotTCon anno = (AnnotTCon)init;
      // TODO: Handle other kinds of typed args
      TermTCon ttc = anno.con().is_generic();
      if( ttc==null ) throw XEC.TODO();
      _init = XClzBuilder.value_tcon(ttc);
    } else {
      _init = null;
    }
    _type = XClzBuilder.jtype(type,false);
    _name = name==null ? null : ((StringCon)name)._str;
    if( name!=null && !_name.equals("$") )
      X.define(_name,_type);
  }
  DefRegAST( String type, String name, String init ) { super(null); _type=type; _name=name; _init=init; }
  
  @Override String type() { return _type; }
  
  @Override void jpre( SB sb ) {
    String type = type();
    if( type==null ) type="var";
    sb.p(type);
    sb.p(" ").p(_name);
    if( _init != null ) sb.p(" = ").p(_init);
  }
}
