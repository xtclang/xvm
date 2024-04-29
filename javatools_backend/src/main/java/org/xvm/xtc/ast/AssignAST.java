package org.xvm.xtc.ast;

import org.xvm.xtc.*;
import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.cons.Const.AsnOp;
import org.xvm.xtc.cons.MethodCon;

import java.util.Arrays;

class AssignAST extends AST {
  final AsnOp _op;
  final MethodCon _meth;
  String _cond_asgn, _name;
  static AssignAST make( ClzBuilder X, boolean asgn ) {
    AST[] kids = new AST[2];
    kids[0] = ast_term(X);
    AsnOp op = asgn ? AsnOp.Asn : AsnOp.OPS[X.u31()];
    kids[1] = ast_term(X);
    MethodCon meth = (MethodCon)X.con();
    // Expecting Assign { DefRegAST no_name reg#n, from RegAST named }
    if( op == AsnOp.Deref ) {
      int idx = ((DefRegAST)kids[0])._reg;
      X._locals.set(idx, ((RegAST)kids[1])._name);
    }

    return new AssignAST(op, meth, kids);
  }
  AssignAST( AST... kids ) { this(AsnOp.Asn,null,kids); }
  private AssignAST( AsnOp op, MethodCon meth, AST... kids ) {
    super(kids);
    _op=op;
    _meth=meth;
    _name = kids[0] instanceof MultiAST || kids[0] instanceof BinOpAST ? null : kids[0].name();
  }


  @Override XType _type() { return _kids[0]._type; }
  // If is a simple define, return the type else null
  XType isDef() {
    return _op==AsnOp.Asn && _kids[0] instanceof DefRegAST def ? def._type : null;
  }

  @Override public AST rewrite() {
    // Assign of a non-primitive array
    if( _kids[0] instanceof BinOpAST bin &&
        // Replace with "ary.set(idx,val)"
        bin._op0.equals(".at(") )
      return new InvokeAST("set",(XType)null,bin._kids[0],bin._kids[1],_kids[1]);

    // Add/push element to array; or op-assign "x+=y"
    if( _meth!=null && _op._meth && _kids[0]._type instanceof XClz clz && clz.unbox()== clz ) {
      AST k0 = _kids[0] instanceof NarrowAST n ? n._kids[0] : _kids[0];
      RegAST reg0 = (RegAST)k0;
      AST op = new InvokeAST( _meth.name(), clz, _kids );
      for( AST kid : _kids ) kid._par = op;
      RegAST reg1 = new RegAST(reg0._reg,reg0._name,reg0._type);
      return new AssignAST( AsnOp.Asn, _meth, reg1, op ).doType();
    }

    // Multi-returns from tuples.
    //   XTC: (Int a, String b, Double c) = retTuple();
    //  Java: { ...tmps...;  tmp = retTuple; a = tmp._f0; b = tmp._f1; c = tmp._f2; }
    if( _kids[0] instanceof MultiAST m ) {
      BlockAST blk = enclosing_block();
      MultiAST mm = new MultiAST(false,Arrays.copyOf(m._kids,m._kids.length+1));
      System.arraycopy(mm._kids,0,mm._kids,1,m._kids.length);
      String tmp = blk.add_tmp(_kids[1]._type);
      AST reg = new RegAST(-1,tmp,_kids[1]._type), con;
      mm._kids[0] = new AssignAST(reg,_kids[1]).doType();
      reg._par = _kids[1]._par = mm._kids[0];
      for( int i=0; i<m._kids.length; i++ ) {
        AST kid = m._kids[i];
        reg = new RegAST(-1,blk.add_tmp(kid._type,kid.name()),kid._type);
        con = new ConAST(tmp+"._f"+i,kid._type);
        mm._kids[i+1] = new AssignAST(reg,con).doType();
        con._par = reg._par = mm._kids[i+1];
      }
      return mm.doType();
    }


    // var := (true,val)  or  var ?= not_null;
    if( _op == AsnOp.AsnIfNotFalse || _op == AsnOp.AsnIfNotNull) {
      XType type = _kids[0]._type;
      if( _kids[0] instanceof DefRegAST ) {
        // Use the _par parent to find nearest enclosing If or Assert for a conditional assignment
        AST top = _par;
        while( !(top instanceof IfAST) && !(top instanceof AssertAST) )
          top = top._par;
        BlockAST blk = top.enclosing_block();
        // IfAST
        if( top instanceof IfAST iff ) {
          // XTC   if( String s := cond_ret(), Int n :=... ) { ..s...}
          // BAST  (If (Multi (Op$AsgnIfNotFalse (DefReg s) (Invoke cond_ret))...) ...true... ...false...)
          // BAST  (If (&&    (Op$AsgnIfNotFalse (Reg stmp) (Invoke cond_ret))...) (Block (DefReg s stmp) ...true...) ...false...)
          // Java  if( $t(stmp = cond_ret()) && GET$COND() && $t(ntmp = cond_ret()) && GET$COND() ) { String s=stmp; long n=ntmp; ...s... }
          String tmp = blk.add_tmp(type);
          _kids[0] = new RegAST(0,tmp,type);  _kids[0]._par = this;

          // Insert new block under IF, with extra slot for DefReg.
          AST ift = iff._kids[1]; // If-true
          // If if-true is already a block, then expand extra slot, else make fresh block
          AST[] kids = ift instanceof BlockAST ? new AST[ift._kids.length+1] : new AST[]{null,ift};
          if(          ift instanceof BlockAST ) System.arraycopy(ift._kids,0,kids,1,ift._kids.length);
          BlockAST blk0 = new BlockAST(kids);
          iff._kids[1] = blk0;  blk0._par = iff;
          blk0._kids[0] = new DefRegAST(type,_name,tmp);
          for( AST kid : kids ) kid._par = blk0;
          // Walk the tree under iff condition and replace uses _name with the tmp
          iff._kids[0].visit(ast -> {
              if( ast instanceof RegAST reg && reg._name.equals(_name) )
                reg._name = tmp;
              return ast;
            },iff);
        } else {
          // XTC   assert Int n := S1(), ...n...
          // BAST  (Assert (XTC) (Multi (Op$AsgnIfNotFalse (DefReg n) (Invoke cond_ret)), other bools ops all anded, including more assigns))
          // BAST  (Invoke (XTC) (Multi (Op$AsgnIfNotFalse (DefReg n) (Invoke cond_ret)), other bools ops all anded, including more assigns))
          // BAST  (Invoke (XTC) (&&    (Op$AsgnIfNotFalse (Reg n) (Invoke cond_ret))... )), other bools ops all anded, including more assigns))
          // Java  long n;  xassert( $t(n=S1()) && GET$COND() && ...n...)
          String tmp = blk.add_tmp(type, _name);
          _kids[0] = new RegAST(0,tmp,type);
          _kids[0]._par = this;
        }
      } else {
        if( !(_par instanceof MultiAST) && !(_par instanceof WhileAST) ) {
          // 's' already defined
          // XTC   s := cond_ret();
          // BAST  (Op$AsgnIfNotFalse (Reg s) (Invoke cond_ret))
          // BAST  (If (Op$AsgnIfNotFalse (Reg s) (Invoke cond_ret)) (Assign (Reg s) (Ret tmp)))
          // Java  if( $t(tmp = cond_ret()) && GET$COND() ) s=tmp;
          String tmp = enclosing_block().add_tmp(type);
          _cond_asgn = _name;
          _kids[0] = new RegAST(0,tmp,type);
          _kids[0]._par = this;
        } // part of a large conditional expression
      }
    }

    return this;
  }

  // Box as needed
  @Override XType reBox( AST kid ) {
    if( _kids[1]!=kid ) return null;
    if( _op == AsnOp.AsnIfNotNull )
      return _type.nullable();
    return _type;
  }

  @Override public SB jcode( SB sb ) {
    return switch( _op ) {
    case AsnIfNotFalse, AsnIfNotNull -> {
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
      yield sb;
    }

    case AsnIfWasFalse -> asnIf(sb,"!",""      ); // if(!var      ) var = e0;
    case AsnIfWasTrue  -> asnIf(sb,"" ,""      ); // if( var      ) var = e0;
    case AsnIfWasNull  -> asnIf(sb,"" ,"==null"); // if( var==null) var = e0;
    // Converts? a "normal" thing into a "Var" thing.
    // Nothing, because loads & stores are all $get and $set now
    case Deref -> sb;

    case Asn -> _kids[0] instanceof RegAST reg && reg._type.isVar()
      // Assigning a Var uses "$set()"
      ? _kids[1].jcode(sb.ip(reg._name).p(".$set(")).p(")")
      : asn(sb);

    case AddAsn -> asn(sb);
    case SubAsn -> asn(sb);
    case MulAsn -> asn(sb);
    case DivAsn -> asn(sb);
    case  OrAsn -> asn(sb);
    default -> throw XEC.TODO();
    };
  }

  private SB asn(SB sb) {
    _kids[0].jcode(sb).p(" ").p(_op.text).p(" ");
    return _kids[1].jcode(sb);
  }

  private SB asnIf(SB sb, String pre, String post) {
    sb.ip("if( ").p(pre).p(_name).p(post).p(" ) ").p(_name).p(" = ");
    return _kids[1].jcode(sb);
  }
}
