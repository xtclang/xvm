package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVLambda;
import org.xvm.cc_explore.xclz.Op;

public class MethodPart extends MMethodPart {
  // Linked list of siblings at the same DAG level with the same name
  public MethodPart _sibling;
  
  // Method's "super"
  private final MethodCon  _supercon;
  private MMethodPart _super;
  
  public final PartCon[] _supers;
  public final Part[] _super_parts;
  
  // Optional "finally" block
  public final MethodCon _finally;
  private MMethodPart _final;

  public final MethodCon _methcon;
  //public final int _lamidx; // Lambda index; for distinguish "->" lambdas with identical signatures
  
  // Annotations
  public final Annot[] _annos;

  // Return types
  public final Parameter[] _rets;

  // Argument types
  public final Parameter[] _args;
  public final int _nDefaults;  // Number of default arguments

  // Constants for code
  public final Const[] _cons;

  // The XEC bytecodes
  public final byte[] _code;
  
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
      for( int i=0; i<rawRets.length; i++ ) {
        _rets[i] = new Parameter(true, i, i==0 && cret, X);
        if( !_rets[i]._con.equals(rawRets[i]) )
          throw new IllegalArgumentException("type mismatch between method constant and return " + i + " value type");
      }

    // Read type parameters
    int nparms = X.u31();
    _nDefaults = X.u31();    // Number of default parms
    TCon[] rawParms = id.rawParms();
    _args = rawParms==null ? null : new Parameter[rawParms.length];
    if( rawParms!=null )
      for( int i=0; i<rawParms.length; i++ ) {
        _args[i] = new Parameter(false, i, i<nparms, X);
        if( !_args[i]._con.equals(rawParms[i]) )
          throw new IllegalArgumentException("type mismatch between method constant and param " + i + " value type");
      }
    
    // Read method's "super(s)"
    _supercon = (MethodCon)X.xget();
    _supers = _supercon == null ? null : PartCon.parts(X);
    _super_parts = _supers==null ? null : new Part[_supers.length];

    // Read the constants
    _cons = X.consts();

    // Read the code
    _code = X.bytes();
    assert _cons==null || _code.length>0;

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

  // Constructor for missing set/get methods
  public MethodPart( Part par, String name ) {
    super(par,name);
    _methcon = null;
    _supercon = null;
    _supers = null;
    _super_parts = null;
    _finally = null;
    _annos = null;
    _rets = null;
    _args = null;
    _nDefaults = 0;
    _cons = null;
    _code = null; // TODO Code for e.g. set/get
    _lines = null;
    _indts = null;
    _first = 0;
  }

  protected boolean isConditionalReturn() {
    return (_nFlags & COND_RET_BIT) != 0;
  }

  // Match against signature.
  // -1 - Hard fail; mismatch arg/rets; or hard mismatch in an arg
  //  0 - Weak yes; at least 1 leaf/PropCon match
  //  1 - Hard yes; an exact arg match all args
  public int match_sig_length(SigCon sig) {
    assert sig._name.equals(_name);
    int amlen =     _args==null ? 0 :     _args.length;
    int aslen = sig._args==null ? 0 : sig._args.length;
    if( amlen != aslen ) return -1;
    int rmlen =     _rets==null ? 0 :     _rets.length;
    int rslen = sig._rets==null ? 0 : sig._rets.length;
    if( rmlen != rslen ) return -1;
    return 0;                   // Not yet matched insides
  }
  public int match_sig(SigCon sig) {
    int rez=1;
    for( int i=0; i<_args.length; i++ ) {
      rez = Math.min(rez,_args[i]._con.eq(sig._args[i]));
      if( rez == -1 ) return -1;
    }
    // Do not match on returns
    return rez;
  }
  
  // Tok, kid-specific internal linking.
  @Override void link_innards( XEC.ModRepo repo ) {
    if( _supercon!=null ) _super = (MMethodPart)_supercon.link(repo);
    if( _finally !=null ) _final = (MMethodPart)_finally .link(repo);
    
    if( _args !=null )
      // Lambda-local type variables
      // From ectasy.x,
      // static <NotNullable> conditional NotNullable notNull(NotNullable? value) {
      //   return value == Null ? False : (True, value);
      // }
      //
      // <NotNullable> is a local type var.  Renaming it to <Pizza> to remove bad
      // thinking around the suggestive name.  This function is in an enum called
      // "Nullable", renaming to "MyEnum" to void naming issues; MyEnum has a
      // single member called CON.  Renaming "notNull" to "flower".
      //
      // static <Pizza> conditional Pizza flower(Pizza? value) {
      //   return value == CON ? False : (True, value);
      // }
      //
      // We see 2 uses of the polymorphic type Pizza.
      // The "conditional" keyword makes the return type a tuple "(Bool,Pizza)".
      // The question mark after Pizza means the input is a union "Pizza|MyEnum".
      //
      // What I see in the MethodPart:
      // - arg0 is called Pizza and typed clazz Type; this makes it an explicit type variable.
      //        Also, it's a fresh open copy of the Type's dependent type.  So Object in this case.
      //        But you could imagine <Pizza extends House>.
      // - arg1 is called value, and its children types can use Pizza.
      // - ret0 is called #-1, and its typed Boolean
      // - ret1 is called #-2, and its children types can use Pizza.
      // - arg0  Type<> -> # -> Object -> ...
      // - arg1  Pizza|# -> MyEnum -> ecstasy.xtclang.org
      // - ret0  # -> Boolean -> ecstasy.xtclang.org
      // - arg0  # -> Pizza -> flower -> flower -> Pizza -> ecstasy.xtclang.org

      // All remaining arguments and returns can use any lambda-local type
      // variable names by looking in the arg[0] slot.  
      for( Parameter arg : _args )
        arg.link( repo );

    if( _annos !=null ) for( Annot    anno : _annos ) anno.link(repo);
    if( _rets  !=null ) for( Parameter ret :  _rets ) ret .link(repo);
    if( _supers  !=null )
      for( int i=0; i<_supers.length; i++ )
        if( _supers[i]!=null )
          _super_parts[i] = _supers[i].link(repo);
    if( _sibling != null )
      _sibling.link(repo);
  }

  // Link method constants
  void _cons( XEC.ModRepo repo ) {
    if( _cons !=null )
      for( Const con : _cons )
        con.link(repo);
  }
  
  TVLambda _setype() {
    if( has_tvar() ) return (TVLambda)tvar();
    TVLambda lam = new TVLambda(_args==null ? 0 : _args.length, _rets==null ? 0 : _rets.length);
    setype_stop_cycles(lam);    // Stop cycles
    // Fill in the TVLambda
    if( _args != null )
      for( int i=0; i<_args.length; i++ )
        _args[i].setype().unify(lam.arg(i));
    if( _rets != null )
      for( int i=0; i<_rets.length; i++ )
        _rets[i].setype().unify(lam.arg(lam._nargs+i));
    assert !lam.unified();
    if( _sibling != null )
      _sibling.setype();
    return lam;
  }

  public boolean is_empty_function() {
    return _code.length == 2 && 
      _code[0] == 3 && // Number of xops is 1
      _code[1] == Op.RETURN_0.ordinal();
  }
}
