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
    ClzBuilder.add_import(XCons.ORDERABLE);
    return CallAST.make(XCons.BOOL,"Orderable",s,_kids[0]);
  }

  @Override public void jpre( SB sb ) { sb.p(_op); }
}
