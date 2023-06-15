package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

import java.util.HashMap;

/**
   Class part
 */
class ClassPart extends Part {
  final HashMap<String,TCon> _parms; // String->Type mapping
  final LitCon _path;           // File name compiling this file
  ClassPart( Part par, int nFlags, IdCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,cond,X);
    _parms = parseTypeParms(X);
    _path  = (LitCon)X.xget();
  }

  // Helper method to read a collection of type parameters.
  HashMap<String,TCon> parseTypeParms( CPool X ) {
    int len = X.u31();
    if( len <= 0 ) return null;
    HashMap<String, TCon> map = new HashMap<>();
    for( int i=0; i < len; i++ ) {
      String name = ((StringCon)X.xget())._str;
      TCon   type =  (     TCon)X.xget();
      TCon old = map.put(name, type);
      assert old==null;         // No double puts
    }
    return map;
  }
}
