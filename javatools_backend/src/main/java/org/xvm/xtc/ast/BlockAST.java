package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.util.SB;
import org.xvm.util.Ary;

import java.util.Arrays;
import java.util.HashMap;

public class BlockAST extends AST {
  static BlockAST make( ClzBuilder X ) {
    int nlocals = X.nlocals();  // Count of locals
    // Parse kids in order as stmts not exprs
    AST[] kids = new AST[X.u31()];
    for( int i=0; i<kids.length; i++ )
      kids[i] = ast(X);    
    X.pop_locals(nlocals);      // Pop scope-locals at end of scope
    return new BlockAST(kids);
  }
  
  public BlockAST( AST... kids ) { super(kids); }

  public BlockAST add(AST kid) {
    BlockAST blk = new BlockAST(Arrays.copyOf(_kids,_kids.length+1));
    blk._tmps  = _tmps  ;
    blk._finals= _finals;
    blk._kids[_kids.length] = kid;
    return blk;
  }
  
  @Override XType _type() { return XCons.VOID; }
  
  HashMap<XType,Ary<String>> _tmps; // Temp names by type
  
  String add_tmp(XType type) { return add_tmp(type,"$tmp"+_uid++); }

  String add_tmp(XType type, String name) { 
    assert type != null;
    if( _tmps==null ) _tmps = new HashMap<>();
    Ary<String> tmps = _tmps.computeIfAbsent( type, k -> new Ary<>( new String[1], 0 ) );
    return tmps.push(name);
  }

  // Final versions of some register, to pass into lambdas.  Light name mangling.
  Ary<RegAST> _finals;
  String add_final(RegAST reg) {
    if( _finals==null ) _finals = new Ary<>(RegAST.class);
    _finals.push(reg);
    return "f$"+reg._name;
  }

  
  @Override void jpre( SB sb ) {
    sb.p("{").ii().nl();
    // Print tmps used by enclosing expressions
    if( _tmps!=null ) {
      for( XType type : _tmps.keySet() ) {
        Ary<String> tmps = _tmps.get(type);
        type.clz(sb.i()).p(" ");
        for( String tmp : tmps )
          sb.p(tmp).p(", ");
        sb.unchar(2).p(";").nl();
      }
    }
    if( _finals!=null )
      for( RegAST reg : _finals )
        sb.ifmt("var f$%0 = %0;\n",reg._name).i();
  }
  @Override void jmid( SB sb, int i ) { if( !sb.was_nl() ) sb.p(";").nl(); }
  @Override void jpost  ( SB sb ) { sb.di().ip("}"); }
}
