package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;

class OrderAST extends AST {
  String _op;

  static OrderAST make( ClzBuilder X, String op ) { return new OrderAST(op,X.kids(1)); }
  
  private OrderAST( String op, AST... kids ) { super(kids); _op = op; }

  @Override XType _type() { return XCons.BOOL; }

  @Override AST prewrite() {
    // Check for the integer special case
    if( _kids[0] instanceof CallAST call &&
        call._kids[0] instanceof ConAST con &&
        con._con.endsWith("compare") &&
        con._type instanceof XFun fun &&
        fun.nargs()==3 &&
        XCons.JLONG.isa(fun.arg(1)) &&
        XCons.JLONG.isa(fun.arg(2)) ) 
      return new BinOpAST(_op,"",XCons.BOOL,call._kids[2],call._kids[3]);
    
    // Order < or > converts an Ordered to a Boolean
    String s = switch(_op) {
    case ">" -> "greater";
    case "<" -> "lesser";
    default -> throw XEC.TODO();
    };
    CallAST call = new CallAST(null,s,new ConAST("Orderable."+s),_kids[0]);
    call._type = XCons.BOOL;
    ClzBuilder.add_import(XCons.ORDERABLE);
    return call;
  }
  
  @Override public void jpre( SB sb ) { sb.p(_op); }
}
