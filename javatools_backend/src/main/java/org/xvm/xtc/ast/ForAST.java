package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;
import java.util.Arrays;

abstract class ForAST extends AST {
  String _label;
  private final int _klabel;

  ForAST( AST[]kids, int klabel ) { super(kids); _klabel = klabel; }

  // Insert an extra Block layer, move the DefReg into that, rename it
  // Java-legal and init; add an increment in the body:
  //      "for( Var v : Iterator )[bool Loop.first,int Loop.count]{...body...}"
  // becomes
  //      "int tmp=0; // count in enclosing block
  //       for( Var v : Iterator ) {
  //         Loop_first = tmp==0;
  //         Loop_count = tmp++;
  //         ...body...;
  //       }"
  @Override public AST rewrite() {
    if( _kids.length==_klabel ) return null;
    assert _label==null;

    DefRegAST def = (DefRegAST)_kids[_klabel];
    String[] ss = def._name.split("_");
    _label = ss[0];
    for( int i=_klabel; i<_kids.length; i++ ) {
      String[] ss2 = ((DefRegAST)_kids[i])._name.split("_");
      if( !ss2[0].equals(_label)  ||
          !(ss2[1].equals("count") || ss2[1].equals("first")) )
        throw XEC.TODO();
    }

    String tmp = enclosing_block().add_tmp(XCons.LONG);
    BlockAST body = (BlockAST)_kids[2];
    AST[] kids = body._kids;
    body._kids = Arrays.copyOf(kids,kids.length+2);
    System.arraycopy(body._kids,0,body._kids,2,kids.length);
    String count = _label+"_count";
    String first = _label+"_first";
    body._kids[0] = new DefRegAST(XCons.BOOL,first,tmp+"==0");
    body._kids[1] = new DefRegAST(XCons.LONG,count,tmp+"++" );
    _kids = Arrays.copyOf(_kids,_klabel);
    return null;
  }

  @Override boolean is_loopswitch() { return true; }

  @Override XType _type() { return XCons.VOID; }

  // Add a label if none exists, to allow a "continue" to target the correct loop
  @Override void add_label() { if( _label==null ) _label = "label"; }
  @Override String label() { return _label; }


}
