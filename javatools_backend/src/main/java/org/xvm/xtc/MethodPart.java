package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;
import org.xvm.util.S;
import java.util.Arrays;

public class MethodPart extends MMethodPart {
  // Linked list of siblings at the same DAG level with the same name
  public MethodPart _sibling;

  // Method's "super"
  private final MethodCon  _supercon;

  public final PartCon[] _supers;
  public final Part[] _super_parts;

  // Optional "finally" block
  public final MethodCon _finally;

  public final MethodCon _methcon;

  // Annotations
  public final Annot[] _annos;

  // Return types
  public final Parameter[] _rets;
  private XType[] _xrets;

  // Argument types
  public final Parameter[] _args;
  public final int _nDefaults;  // Number of default arguments
  private XType[] _xargs;

  // Constants for code
  public final Const[] _cons;

  // The XEC bytecodes
  public final byte[] _code;
  // The XEC AST
  public final byte[] _ast;

  // The source code
  public final String[] _lines; // Source lines
  public final int[] _indts;    // Indents
  public final int _first;      // First line number

  //
  MethodPart( Part par, int nFlags, MethodCon id, CondCon con, CPool X ) {
    super(par,nFlags,id,con,X);

    // Unique "id" since name is not unique
    _methcon = id;

    // Linked-list of same-name methods.
    _sibling = null;

    // Read annotations
    _annos = Annot.xannos(X);

    // Read optional "finally" block
    _finally = (MethodCon)X.xget();

    // Read return types
    boolean cret = isConditionalReturn();
    TCon[] rawRets = id.rawRets();
    _rets = rawRets==null ? null : new Parameter[rawRets.length];
    if( rawRets!=null )
      for( int i=0; i<rawRets.length; i++ )
        _rets[i] = new Parameter(/*true, i,*/ i==0 && cret, rawRets[i], X);

    // Read type parameters
    int nparms = X.u31();
    _nDefaults = X.u31();    // Number of default parms
    TCon[] rawParms = id.rawParms();
    _args = rawParms==null ? null : new Parameter[rawParms.length];
    if( rawParms!=null )
      for( int i=0; i<rawParms.length; i++ )
        _args[i] = new Parameter(/*false, i,*/ i<nparms, rawParms[i], X);

    // Read method's "super(s)"
    _supercon = (MethodCon)X.xget();
    _supers = _supercon == null ? null : PartCon.parts(X);
    _super_parts = _supers==null ? null : new Part[_supers.length];

    // Read the constants
    _cons = X.consts();

    // Read the code
    _code = X.bytes();
    assert _cons==null || _code.length>0;

    // Read and skip the AST bytes
    _ast = X.bytes();

    // Read the source code
    int n = X.u31();
    _first = n > 0 ? X.u31() : -1;
    _lines = new String[n];
    _indts = new int[n];
    for( int i=0; i<n; i++ ) {
      _indts[i] = X.u31();
      StringCon str = (StringCon)X.xget();
      _lines[i] = str==null ? null : str._str;
    }
  }

  // Constructor for missing set/get/equals/hashCode methods
  public MethodPart( Part par, String name, Parameter[] args, Parameter[] rets ) {
    super(par,name);
    _methcon = null;
    _supercon = null;
    _supers = null;
    _super_parts = null;
    _finally = null;
    _annos = null;
    _args = args;
    _rets = rets;
    _nDefaults = 0;
    _cons = null;
    _code = null;
    _ast = null;
    _lines = null;
    _indts = null;
    _first = 0;
  }

  protected boolean isConditionalReturn() {
    return (_nFlags & COND_RET_BIT) != 0;
  }

  // Match against signature.
  public boolean match_sig(SigCon sig) {
    assert sig._name.equals(_name);
    boolean rez = match_sig(_args,sig._args, true);
    if( !rez ) return false;
    //assert match_sig(_rets,sig._rets, false); // No selection based on return, it just needs to match
    return true;
  }
  private boolean match_sig(Parameter[] ps, TCon[] ts, boolean box) {
    if( ps==null && ts==null ) return true;
    if( ps==null || ts==null ) return false;
    if( ps.length != ts.length ) return false;
    for( int i=0; i<ps.length; i++ ) {
      if( ps[i].tcon() == ts[i] ) continue; // Trivially true
      XType xp = ps[i].type(box);
      XType xt = XType.xtype(ts[i],box);
      if( !xt.isa(xp) )
        return false;
    }
    return true;
  }

  // Link method constants
  void _cons( XEC.ModRepo repo ) {
    if( _cons !=null )
      for( Const con : _cons )
        if( con instanceof MethodCon meth )
          // Link signature, but not the method proper
          ; //meth._sig.link(repo);
        else
          //con.link(repo);
          throw XEC.TODO();
  }

  public boolean is_empty_function() {
    return _code.length==2 && _code[0]==1 && _code[1]==76;
  }
  public boolean is_constructor() { return S.eq(_name,"construct"); }
  boolean isOperator() {
    // Operator methods always unbox (since cannot dispatch as operator).
    // TODO: Probably can dispatch as normal method, will need boxed version.
    return _annos!=null && _annos[0]._par.name().equals("Operator");
  }

  // XTypes for all arguments.  Lazily built.
  // Default arguments responsibility of the caller; they are explicit here.

  // Default constructors take class type parameters, but will lack explicit
  // args here; so _args and _xargs will not necessarily be the same.  XARGS
  // is broken into sets:
  // - Parent xargs/types, one explicit type argument per parent type var
  // - Normal args, mapping to _args
  // - Nested inner classes get the outer class as an arg
  public XType[] xargs() { return _xargs==null ? (_xargs = _xargs()) : _xargs; }
  private XType[] _xargs() {
    // Non-constructors just walk the args (and optionally box)
    if( !is_constructor() )
      // Don't box privates or operators
      return (_xargs = XType.xtypes(_args, !(isPrivate() || isOperator()) ));

    // Constructors get all the type args from their class
    XClz clz = clz()._tclz;
    int len = clz._tns.length, j=len;
    // Also get their stated args
    if( _args != null ) len += _args.length;
    // Nested inner classes get the outer class as an arg.
    ClassPart outer = isNestedInnerClass();
    if( outer!=null ) len++;

    // Extend as needed
    XType[] zts = Arrays.copyOf(clz._xts,len);

    // Add outer class next
    if( outer!=null )
      zts[j++] = XClz.make(outer);
    // Copy/type actual args
    if( _args != null )
      for( Parameter arg : _args )
        zts[j++] = arg.type( false );

    return zts;
  }
  public XType xarg(int idx) { return xargs()[idx]; }

  public XType[] xrets() {
    if( _xrets != null ) return _xrets;
    return (_xrets = XType.xtypes(_rets,false));
  }
  public XType xret(int idx) { return xrets()[idx]; }

  public ClassPart isNestedInnerClass() {
    // self -> MMethod -> Class -> [Package or other ???]
    ClassPart clz = clz();
    Part outer = clz._par;
    if( clz.isStatic() || outer instanceof PackagePart || clz._tclz.isa(XCons.CONST) )
      return null;
    while( !(outer instanceof ClassPart outclz) ) outer = outer._par;
    return outclz;
  }

  // Methods returning a conditional have to consume the conditional
  // immediately, or it's lost.  The returned value will be optionally assigned.
  //
  // 's' already defined
  // XTC   s := cond_ret();
  // BAST  (Op$AsgnIfNotFalse (Reg s) (Invoke cond_ret))
  // BAST  (If (Op$AsgnIfNotFalse (Reg s) (Invoke cond_ret)) (Assign (Reg s) (Ret tmp)))
  // Java  $t(tmp = cond_ret()) && $COND && $t(s=tmp)
  //
  // XTC   if( String s := cond_ret(), Int n :=... ) { ..s...}
  // BAST  (If (Multi (Op$AsgnIfNotFalse (DefReg s) (Invoke cond_ret))...) ...true... ...false...)
  // BAST  (If (&&    (Op$AsgnIfNotFalse (Reg stmp) (Invoke cond_ret))...) (Block (DefReg s stmp) ...true...) ...false...)
  // Java  if( (stmp = cond_ret()) && $COND && (ntmp = cond_ret() && $COND ) { String s=stmp; long n=ntmp; ...s... }
  //
  // XTC   assert Int n := S1(), ...n...
  // BAST  (Assert (XTC) (Multi (Op$AsgnIfNotFalse (DefReg n) (Invoke cond_ret)), other bools ops all anded, including more assigns))
  // BAST  (Assert (XTC) (&&    (Op$AsgnIfNotFalse (Reg n) (Invoke cond_ret))... )), other bools ops all anded, including more assigns))
  // Java  long n0, n1;  assert $t(n0=S1()) && $COND && ...n0...


  public boolean is_cond_ret() { return (_nFlags & Part.COND_RET_BIT) != 0; }

  // Reasonable java name.
  public String jname() {
    // Method is nested in a method, qualify the name more
    return _par._par instanceof ClassPart ? _name
      :_par._par._name+"$"+_name;
  }

  public ClassPart clz() {
    Part p = _par;
    while( !(p instanceof ClassPart clz) )
      p = p._par;
    return clz;
  }
}
