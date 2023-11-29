package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.*;

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
        con._type instanceof XType.Fun fun &&
        fun._args.length==3 &&
        fun._args[1]==XType.JLONG &&
        fun._args[2]==XType.JLONG ) {
      return new BinOpAST(_op,"",XType.BOOL,call._kids[1],call._kids[2]);
    }
    
    // Order < or > converts an Ordered to a Boolean
    String s = switch(_op) {
    case ">" -> "greater";
    case "<" -> "lesser";
    default -> throw XEC.TODO();
    };
    CallAST call = new CallAST(null,s,new ConAST("Orderable."+s),_kids[0]);
    call._type = XType.BOOL;
    ClzBuilder.IMPORTS.add("ecstasy.Orderable");
    return call;
  }
  
  @Override public void jpre( SB sb ) { sb.p(_op); }
}
