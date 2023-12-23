package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;

class OrderAST extends AST {
  String _op;

  static OrderAST make( ClzBuilder X, String op ) { return new OrderAST(X.kids(1),op); }
  
  private OrderAST( AST[] kids, String op ) { super(kids); _op = op; }

  @Override XType _type() { return XType.BOOL; }

  @Override AST rewrite() {
    // Check for the integer special case
    if( _kids[0] instanceof CallAST call &&
        call._kids[0] instanceof ConAST con &&
        con._con.endsWith("compare") &&
        con._type instanceof XFun fun &&
        fun.nargs()==3 &&
        XType.JLONG.subClasses(fun.arg(1)) &&
        XType.JLONG.subClasses(fun.arg(2)) ) 
      return new BinOpAST(_op,"",XType.BOOL,call._kids[2],call._kids[3]);
    
    // Order < or > converts an Ordered to a Boolean
    String s = switch(_op) {
    case ">" -> "greater";
    case "<" -> "lesser";
    default -> throw XEC.TODO();
    };
    CallAST call = new CallAST(null,s,new ConAST("Orderable."+s),_kids[0]);
    call._type = XType.BOOL;
    ClzBuilder.add_import(XType.ORDERABLE);
    return call;
  }
  
  @Override public void jpre( SB sb ) { sb.p(_op); }
}
