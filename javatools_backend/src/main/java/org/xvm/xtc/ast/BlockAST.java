package org.xvm.xtc.ast;

import java.util.Arrays;
import java.util.HashMap;
import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.S;
import org.xvm.util.SB;
import org.xvm.xtc.*;

public class BlockAST extends ElvisAST {
  static BlockAST make( ClzBuilder X ) {
    int nlocals = X.nlocals();  // Count of locals
    // Parse kids in order as stmts not exprs
    AST[] kids = new AST[X.u31()];
    for( int i=0; i<kids.length; i++ )
      kids[i] = ast(X);
    X.pop_locals(nlocals);      // Pop scope-locals at end of scope
    return new BlockAST(kids);
  }

  public BlockAST( AST... kids ) { super(kids); _type = _type(); }

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

  @Override public AST rewrite() {
    if( _elves != null ) {
      for( int i=_elves._len-1; i>=0; i-- ) {
        Elf elf = _elves.at(i);
        AST expr = _kids[elf._idx];
        AST eq = elf.test();
        _kids[elf._idx] = new IfAST(eq,expr);
      }
      _elves=null;              // Been there, done that
      return this;
    }

    if( _kids.length>0 && _kids[_kids.length-1] instanceof ReturnAST ret &&
        ret._meth.xrets()==null && ret._expr==null ) {
      // Void return functions execute the return for side effects only
      _kids = Arrays.copyOf(_kids,_kids.length-1);
      return this;
    }
    return null;
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
        sb.ifmt("var f$%0 = %0;",reg._name).nl();
  }
  @Override void jmid( SB sb, int i ) { if( !sb.was_nl() ) sb.p(";").nl(); }
  @Override void jpost  ( SB sb ) { sb.di().ip("}"); }
}
