package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.util.SB;
import org.xvm.xtc.cons.Const.AsnOp;

class AssignAST extends AST {
  final AsnOp _op;
  String _cond_asgn, _name;
  static AssignAST make( ClzBuilder X, boolean asgn ) {
    AST[] kids = new AST[2];
    kids[0] = ast_term(X);
    AsnOp op = asgn ? AsnOp.Asn : AsnOp.OPS[X.u31()];
    kids[1] = ast_term(X);
    return new AssignAST(op, kids);
  }
  AssignAST( AST... kids ) { this(AsnOp.Asn,kids); }
  private AssignAST( AsnOp op, AST... kids ) { super(kids); _op=op; _name = kids[0].name(); }


  @Override XType _type() { return _kids[0]._type; }
  @Override boolean _cond() { return _op==AsnOp.AsnIfNotFalse; }
  
  @Override AST rewrite() {
    // Assign of a non-primitive array
    if( _kids[0] instanceof BinOpAST bin &&
        // Replace with "ary.set(idx,val)"
        bin._op0.equals(".at(") )
      return new InvokeAST("set",(XType)null,bin._kids[0],bin._kids[1],_kids[1]);

    // Add/push element to array
    if( _op==AsnOp.AddAsn && _kids[0]._type instanceof XAry ary && ary.e()==_kids[1]._type )
      return new InvokeAST("add",ary.e(),_kids);
    
    // Autobox into _kids[0] from Base in _kids[1]
    autobox(1,_kids[0]._type);
    
    // var := (true,val)  or  var ?= not_null;
    if( _op == AsnOp.AsnIfNotFalse || _op == AsnOp.AsnIfNotNull) {
      XType type = _kids[0]._type;
      BlockAST blk = enclosing_block();
      if( _kids[0] instanceof DefRegAST ) {
        // Use the _par parent to find nearest enclosing If or Assert for a conditional assignment
        AST top = _par;
        while( !(top instanceof IfAST) && !(top instanceof InvokeAST call && call._meth.equals("xassert")) )
          top = top._par;
        // IfAST
        if( top instanceof IfAST iff ) {
          // XTC   if( String s := cond_ret(), Int n :=... ) { ..s...}
          // BAST  (If (Multi (Op$AsgnIfNotFalse (DefReg s) (Invoke cond_ret))...) ...true... ...false...)
          // BAST  (If (&&    (Op$AsgnIfNotFalse (Reg stmp) (Invoke cond_ret))...) (Block (DefReg s stmp) ...true...) ...false...)
          // Java  if( $t(stmp = cond_ret()) && GET$COND() && $t(ntmp = cond_ret()) && GET$COND() ) { String s=stmp; long n=ntmp; ...s... }
          String tmp = blk.add_tmp(type);
          _kids[0] = new RegAST(0,tmp,type);
          BlockAST iftblk = iff.true_blk();
          assert iftblk._kids[0]==null;
          iftblk._kids[0] = new DefRegAST(type,_name,tmp);
        } else {
          // XTC   assert Int n := S1(), ...n...
          // BAST  (Assert (XTC) (Multi (Op$AsgnIfNotFalse (DefReg n) (Invoke cond_ret)), other bools ops all anded, including more assigns))
          // BAST  (Invoke (XTC) (Multi (Op$AsgnIfNotFalse (DefReg n) (Invoke cond_ret)), other bools ops all anded, including more assigns))
          // BAST  (Invoke (XTC) (&&    (Op$AsgnIfNotFalse (Reg n) (Invoke cond_ret))... )), other bools ops all anded, including more assigns))
          // Java  long n0, n1;  xassert( $t(n0=S1()) && GET$COND() && ...n0...)
          String tmp = blk.add_tmp(type,_name);
          _kids[0] = new RegAST(0,tmp,type);
        }
      } else {
        // 's' already defined
        // XTC   s := cond_ret();
        // BAST  (Op$AsgnIfNotFalse (Reg s) (Invoke cond_ret))
        // BAST  (If (Op$AsgnIfNotFalse (Reg s) (Invoke cond_ret)) (Assign (Reg s) (Ret tmp)))
        // Java  if( $t(tmp = cond_ret()) && GET$COND() ) s=tmp;
        String tmp = blk.add_tmp(type);
        _cond_asgn = _name;
        _kids[0] = new RegAST(0,tmp,type);
      }
    }
    return this;
  }
  
  @Override public SB jcode( SB sb ) {
    switch( _op ) {
    case AsnIfNotFalse, AsnIfNotNull: {
      // var := (true,val)  or  var ?= not_null;
      if( _cond_asgn!=null ) sb.ip("if( ");
      // Expression result is the boolean conditional value,
      // and the var was previously defined.
      // $t(var = expr()) && XRuntime.GET$COND()
      String name = _kids[0] instanceof RegAST reg ? reg._name : ((DefRegAST)_kids[0])._name;
      if( _op == AsnOp.AsnIfNotFalse ) sb.p("$t");
      _kids[1].jcode(sb.p("(").p(name).p(" = ")).p(")");
      sb.p( _op == AsnOp.AsnIfNotFalse ?" && XRuntime.GET$COND()" : "!=null " );
      
      // $t(tmp = expr()) && XRuntime.GET$COND() && $t(var = tmp)
      if( _cond_asgn != null )
        sb.p(") ").p(_cond_asgn).p(" = ").p(name);
      break;
    }
      
    case AsnIfWasFalse: return asnIf(sb,"!",""      ); // if(!var      ) var = e0;      
    case AsnIfWasTrue : return asnIf(sb,"" ,""      ); // if( var      ) var = e0;      
    case AsnIfWasNull : return asnIf(sb,"" ,"==null"); // if( var==null) var = e0;
      
    default:
      _kids[0].jcode(sb);
      sb.p(" ").p(_op.text).p(" ");
      _kids[1].jcode(sb);
      break;
    }
    return sb;
  }

  private SB asnIf(SB sb, String pre, String post) {
    sb.ip("if( ").p(pre).p(_name).p(post).p(" ) ").p(_name).p(" = ");
    return _kids[1].jcode(sb);
  }
}
