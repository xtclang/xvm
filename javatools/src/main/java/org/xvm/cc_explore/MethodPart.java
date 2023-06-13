package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

import java.io.IOException;

class MethodPart extends Part<MethodCon> {

  // Method's "super"
  public final MethodCon _super;
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
  public final StringCon[] _lines;
  public final int[] _indts;    // Indents
  public final int _first;      // First line
  
  // 
  MethodPart( Part par, int nFlags, MethodCon id, CondCon con, FilePart X ) {
    super(par,nFlags,id,con,X);
    
    // Read annotations
    _annos = Annot.xannos(X);

    // Read optional "finally" block
    _finally = (MethodCon)X.xget();

    // Read return types
    TCon[] rawRets = _id.rawRets();
    _rets = new Parameter[rawRets.length];
    boolean cret = isConditionalReturn();
    for( int i=0; i<rawRets.length; i++ ) {
      _rets[i] = new Parameter(true, i, i==0 && cret, X);
      if( !_rets[i]._type.equals(rawRets[i]) )
        throw new IllegalArgumentException("type mismatch between method constant and return " + i + " value type");
    }

    // Read type parameters
    TCon[] rawParms = _id.rawParms();
    int nparms = X.u31();
    _nDefaults = X.u31();    // Number of default parms
    _args = new Parameter[rawParms.length];
    for( int i=0; i<rawParms.length; i++ ) {
      _args[i] = new Parameter(false, i, i<nparms, X);
      if( !_args[i]._type.equals(rawParms[i]) )
        throw new IllegalArgumentException("type mismatch between method constant and param " + i + " value type");
    }
    
    // Read method's "super(s)"
    _super = (MethodCon)X.xget();
    _supers = _super == null ? new Const[0] : Const.xconsts(X);

    // Read the constants
    _cons = Const.xconsts(X);

    // Read the code
    _code = X.bytes();
    assert _cons.length==0 || _code.length>0;

    // Read the source code
    int n = X.u31();
    _first = n > 0 ? X.u31() : -1;
    _lines = new StringCon[n];
    _indts = new int[n];
    for( int i=0; i<n; i++ ) {
      _indts[i] = X.u31();
      _lines[i] = (StringCon)X.xget();
    }
  }

  protected boolean isConditionalReturn() {
    return (_nFlags & COND_RET_BIT) != 0;
  }

}
