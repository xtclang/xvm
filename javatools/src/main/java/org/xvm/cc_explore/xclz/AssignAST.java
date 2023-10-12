package org.xvm.cc_explore.xclz;

import org.xvm.asm.ast.AssignAST.Operator;
import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;

class AssignAST extends AST {
  static final Operator[] OPS = Operator.values();
  final Operator _op;
  String _cond_asgn;
  static AssignAST make( XClzBuilder X, boolean asgn ) {
    AST[] kids = new AST[2];
    kids[0] = ast_term(X);
    Operator op = asgn ? Operator.Asn : OPS[X.u31()];
    kids[1] = ast_term(X);
    return new AssignAST(op, kids);
  }
  AssignAST( AST... kids ) { this(Operator.Asn,kids); }
  private AssignAST( Operator op, AST... kids ) { super(kids); _op=op; }
  @Override AST rewrite() {
    // Assign of a non-primitive array
    if( _kids[0] instanceof BinOpAST bin &&
        // Replace with "ary.set(idx,val)"
        bin._op0.equals(".at(") )
      return new InvokeAST("set",bin._kids[0],bin._kids[1],_kids[1]);
    // Stupidly in Java: "Long x = 4;" fails
    if( _kids[0].type().equals("Long") && _kids[1] instanceof ConAST con &&
        !con._con.equals("null") && !con._con.endsWith("L") )
      _kids[1] = new ConAST(con._con+"L");

    if( _op == Operator.AsnIfNotFalse || _op == Operator.AsnIfNotNull) {
      String type = _kids[0].type();
      BlockAST blk = enclosing_block();
      if( _kids[0] instanceof DefRegAST def ) {
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
          iftblk._kids[0] = new DefRegAST(type,def._name,tmp);
        } else {
          // XTC   assert Int n := S1(), ...n...
          // BAST  (Assert (XClz) (Multi (Op$AsgnIfNotFalse (DefReg n) (Invoke cond_ret)), other bools ops all anded, including more assigns))
          // BAST  (Invoke (XClz) (Multi (Op$AsgnIfNotFalse (DefReg n) (Invoke cond_ret)), other bools ops all anded, including more assigns))
          // BAST  (Invoke (XClz) (&&    (Op$AsgnIfNotFalse (Reg n) (Invoke cond_ret))... )), other bools ops all anded, including more assigns))
          // Java  long n0, n1;  xassert( $t(n0=S1()) && GET$COND() && ...n0...)
          String tmp = blk.add_tmp(type,def._name);
          _kids[0] = new RegAST(0,tmp,type);
        }
      } else {
        // 's' already defined
        // XTC   s := cond_ret();
        // BAST  (Op$AsgnIfNotFalse (Reg s) (Invoke cond_ret))
        // BAST  (If (Op$AsgnIfNotFalse (Reg s) (Invoke cond_ret)) (Assign (Reg s) (Ret tmp)))
        // Java  if( $t(tmp = cond_ret()) && GET$COND() ) s=tmp;
        String tmp = blk.add_tmp(type);
        _cond_asgn = ((RegAST)_kids[0])._name;
        _kids[0] = new RegAST(0,tmp,type);
      }
    }
    return this;
  }
  
  @Override SB jcode( SB sb ) {
    if( _op == Operator.AsnIfNotFalse || _op == Operator.AsnIfNotNull ) {
      if( _cond_asgn!=null ) sb.ip("if( ");
      // Expression result is the boolean conditional value,
      // and the var was previously defined.
      // $t(var = expr()) && XRuntime.GET$COND()
      String name = _kids[0] instanceof RegAST reg ? reg._name : ((DefRegAST)_kids[0])._name;
      if( _op == Operator.AsnIfNotFalse ) sb.p("$t");
      _kids[1].jcode(sb.p("(").p(name).p(" = ")).p(")");
      sb.p( _op == Operator.AsnIfNotFalse ?" && XRuntime.GET$COND()" : "!=null " );
      
      // $t(tmp = expr()) && XRuntime.GET$COND() && $t(var = tmp)
      if( _cond_asgn != null )
        sb.p(") ").p(_cond_asgn).p(" = ").p(name);
        
    } else {
      _kids[0].jcode(sb);
      sb.p(" ").p(_op.text).p(" ");
      _kids[1].jcode(sb);
    }
    return sb;
  }
}
