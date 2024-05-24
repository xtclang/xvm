package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.util.S;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const.BinOp;
import org.xvm.xtc.cons.NumCon;
import org.xvm.xtc.XCons;

import java.util.HashMap;

class BinOpAST extends AST {
  String _op0, _op1;

  static AST make( ClzBuilder X, boolean has_type ) {
    AST[] kids = new AST[2];
    kids[0] = ast_term(X);
    BinOp op = BinOp.OPS[X.u31()];
    kids[1] = ast_term(X);
    XType type = has_type
      ? XType.xtype(X.con(),false) // Type from the AST file
      : (op==BinOp.CompOrd ? XCons.ORDERED : XCons.BOOL ); // Must be one of the ordering operators
    if( op==BinOp.Else )        // This becomes a ternary op
      return new TernaryAST(new AST[]{kids[0],kids[1]},type);
    return new BinOpAST(op.text,"",type,kids);
  }

  static BinOpAST make( ClzBuilder X, String op0, String op1 ) {
    AST[] kids = X.kids(2);
    return new BinOpAST(op0,op1,null,kids);
  }

  BinOpAST( String op0, String op1, XType type, AST... kids ) {
    super(kids);
    _op0 = op0;
    _op1 = op1;
    _type = type;
  }

  @Override XType _type() {
    if( _op0.equals(".at(") ) {
      // Tuple or Array
      XType tk = _kids[0]._type;
      if( tk.isAry()  )
        return tk.e();
      if( tk == XCons.JSTRING )
        return XCons.JCHAR;
      // Something else (PropCon?) means get the collections' element type
      int idx = 0;              // Default for non-Tuple
      // Tuple at fixed field offset
      if( tk.isTuple() &&
          (idx = isFixedOffset()) == -1 )
        return XCons.XXTC;   // Unknown field (not a fixed number)
      return idx < tk._xts.length ? tk._xts[idx] : XCons.XXTC;
    }

    // Cast to sharper
    if( _op0.equals("as") )
      return _type = _kids[1]._type;

    return _type;
  }

  // Fixed positive offset in _kids[1], or -1
  private int isFixedOffset() {
    return _kids[1] instanceof ConAST con && con._tcon instanceof NumCon num ? (int)num._x : -1;
  }

  @Override public AST unBox() {
    int idx;
    // Java primitive fetch instead of boxed fetch.
    // ary_expr ".at(" idx ")"
    // ary_expr.at8(idx) -- and the expression is primitive, not boxed
    if( _op0.equals(".at(") && _kids[1]._type==XCons.LONG ) {
      _type = _type.unbox();
      if( _kids[0]._type == XCons.STRING ) {
        _op0 = ".charAt((int)(";  _op1 = "))";
        return this;            // progress
      }
      if( _kids[0]._type.isAry() ) {
        _op0 = ".at8(";         // Use primitive 'at' instead of generic
        return this;            // progress
      }
      if( _kids[0]._type.isTuple() && (idx=isFixedOffset()) != -1 )
        // Tuple at fixed field offset
        return new PropertyAST(_kids[0],_type,"_f"+idx);
    }
    return super.unBox();
  }


  @Override public AST rewrite() {

    // Range is not a valid Java operator, so need to change everything here
    if( _op0.equals(".." ) ) return do_range(_kids,XCons.RANGEII);
    if( _op0.equals("..<") ) return do_range(_kids,XCons.RANGEIE);
    if( _op0.equals("<=>") ) {
      ClzBuilder.add_import(XCons.ORDERABLE);
      return new InvokeAST("spaceship",XCons.ORDERED,new ConAST("Orderable"),_kids[0],_kids[1]).doType();
    }

    // This is a ternary null-check or elvis operator.  _kids[0] is
    // directly the predicate test.  I need to restructure this tree:
    //   (  "?:"                pred  alt)
    // into this tree:
    //   ( ((tmp=pred)!=null) ? tmp : alt)
    if( _op0.equals("?:") ) {
      TernaryAST tern = new TernaryAST(_kids,_kids[0]._type);
      tern._par = _par;
      tern._kids[0] = tern.doElvis(_kids[0],_kids[0]);
      return tern;
    }

    // Cast.  Since I treat XTC "Type" as a concrete instance XTC.GOLD,
    // I do not need to cast to "Type".  Other casts can remain.
    if( _op0.equals("as") && _kids[1] instanceof ConAST con && con._con.equals("Type<XTC,XTC>.GOLD") )
      return _kids[0];          // Types already as values

    return null;
  }

  static AST do_range( AST[] kids, XClz rng ) {
    return new NewAST(kids,ClzBuilder.add_import(rng));
  }

  @Override public SB jcode( SB sb ) {
    if( _op0.equals("as") ) {
      sb.p("((").p(((XClz)_kids[1]._type).clz_bare()).p(")");
      return _kids[0].jcode(sb).p(")");
    }
    expr(sb,_kids[0]).p(_op0);
    expr(sb,_kids[1]).p(_op1);
    return sb;
  }

  // Print 1+(2+3) as "1+2+3"
  // Print 1*(2+3) as "1*(2+3)"
  SB expr( SB sb, AST ast ) {
    boolean wrap = ast instanceof BinOpAST bin &&
            !    _op1.equals(")") &&
            !bin._op1.equals(")") &&
            prec( _op0, bin._op0 );
    if( ast instanceof AssignAST ) wrap=true;
    if( _type==XCons.STRING && _kids[1]==ast &&
        (ast instanceof BinOpAST || ast instanceof TernaryAST || (ast instanceof SwitchAST sast && sast._kids[0] instanceof MultiAST ) ) ) {
      sb.p(" "); wrap=true;
    }
    if( wrap ) sb.p("(");
    ast.jcode(sb);
    if( wrap ) sb.p(")");
    return sb;
  }

  // Java precedence table
  private static final HashMap<String,Integer> PRECS = new HashMap<>(){{
      put("._f",17); // Primitive tuple field loads

      put("[" ,16);

      // java unary ops go here

      put("*" ,12);
      put("/" ,12);
      put("%" ,12);

      put("+" ,11);
      put("-" ,11);

      put("<<",10);
      put(">>",10);
      put(">>>",10);

      put("<" , 9);
      put(">" , 9);
      put("<=", 9);
      put(">=", 9);

      put("==", 8);
      put("!=", 8);

      put("&" , 7);
      put("^" , 6);
      put("|" , 5);

      put("&&" ,4);
      put("||" ,3);

      put("?:" ,2);

      put(".charAt((int)(",0); // Not-the-operator
    }};
  private boolean prec(String op, String ex) {
    Integer ii0 = PRECS.get(op);
    Integer ii1 = PRECS.get(ex);
    if( ii0==null ) { System.err.println("Missing \""+op+"\" from BinOpAST"); return true; }
    if( ii1==null ) { System.err.println("Missing \""+ex+"\" from BinOpAST"); return true; }
    return ii0 > ii1;
  }
}
