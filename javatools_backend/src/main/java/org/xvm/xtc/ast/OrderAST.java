package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;

class OrderAST extends AST {
  String _op;

  static OrderAST make( ClzBuilder X, String op ) { return new OrderAST(op,X.kids(1)); }

  private OrderAST( String op, AST... kids ) { super(kids); _op = op; }

  @Override XType _type() { return XCons.BOOL; }

  @Override public AST rewrite() {
    if( _kids[0] instanceof InvokeAST inv && inv._meth.equals("spaceship") &&
        inv._kids[1]._type instanceof XBase &&
        inv._kids[2]._type instanceof XBase )
      return new BinOpAST(_op,"",XCons.BOOL,inv._kids[1],inv._kids[2]);

    // Order < or > converts an Ordered to a Boolean
    String s = switch(_op) {
    case ">" -> "greater";
    case "<" -> "lesser";
    default -> throw XEC.TODO();
    };
    // TODO: BAST bug Call to compare is currently returning a Boolean and not an Ordered
    XType arg = _kids[0]._type;
    ConAST con = new ConAST("Orderable."+s,XFun.make(new XType[]{arg},new XType[]{XCons.BOOL}));
    CallAST call = new CallAST(null,s,con,_kids[0]);
    call._type = XCons.BOOL;
    ClzBuilder.add_import(XCons.ORDERABLE);
    return call;
  }

  @Override public void jpre( SB sb ) { sb.p(_op); }
}
