package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

public class MethodPart extends MMethodPart {

  // Method's "super"
  private final MethodCon  _supercon;
  public final Const[] _supers;
  
  // Optional "finally" block
  public final MethodCon _finally;

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
    
    // Read annotations
    _annos = Annot.xannos(X);

    // Read optional "finally" block
    _finally = (MethodCon)X.xget();

    // Read return types
    TCon[] rawRets = id.rawRets();
    _rets = new Parameter[rawRets.length];
    boolean cret = isConditionalReturn();
    for( int i=0; i<rawRets.length; i++ ) {
      _rets[i] = new Parameter(true, i, i==0 && cret, X);
      if( !_rets[i]._clzcon.equals(rawRets[i]) )
        throw new IllegalArgumentException("type mismatch between method constant and return " + i + " value type");
    }

    // Read type parameters
    TCon[] rawParms = id.rawParms();
    int nparms = X.u31();
    _nDefaults = X.u31();    // Number of default parms
    _args = new Parameter[rawParms.length];
    for( int i=0; i<rawParms.length; i++ ) {
      _args[i] = new Parameter(false, i, i<nparms, X);
      if( !_args[i]._clzcon.equals(rawParms[i]) )
        throw new IllegalArgumentException("type mismatch between method constant and param " + i + " value type");
    }
    
    // Read method's "super(s)"
    _supercon = (MethodCon)X.xget();
    _supers = _supercon == null ? null : X.consts();

    // Read the constants
    _cons = X.consts();

    // Read the code
    _code = X.bytes();
    assert _cons.length==0 || _code.length>0;

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

  protected boolean isConditionalReturn() {
    return (_nFlags & COND_RET_BIT) != 0;
  }

  // Tok, kid-specific internal linking.
  @Override void link_innards( XEC.ModRepo repo ) {
    if( _supercon!=null ) throw XEC.TODO();
    if( _supers  !=null ) throw XEC.TODO();
    if( _finally !=null ) throw XEC.TODO();
    if( _annos   !=null ) for( Annot    anno : _annos ) anno.link(repo);
    if( _rets    !=null ) for( Parameter ret :  _rets ) ret .link(repo);
    if( _args    !=null ) for( Parameter arg :  _args ) arg .link(repo);
    if( _cons    !=null ) for( Const     con :  _cons ) con .link(repo);
  }

}
