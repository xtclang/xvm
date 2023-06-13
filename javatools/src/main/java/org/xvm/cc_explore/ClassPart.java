package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

import java.util.HashMap;

/**
   Class part
 */
class ClassPart<ID2 extends IdCon> extends Part<ID2> {
  final HashMap<StringCon,TCon> _params; // String->Type mapping
  final LitCon _path;           // File name compiling this file
  ClassPart( Part par, int nFlags, ID2 id, CondCon cond, FilePart X ) {
    super(par,nFlags,id,cond,X);
    _params = parseTypeParams(X);
    _path = (LitCon)X.xget();
  }

  // Helper method to read a collection of type parameters.
  HashMap<StringCon, TCon> parseTypeParams( FilePart X ) {
    int len = X.u31();
    if( len <= 0 ) return null;
    HashMap<StringCon, TCon> map = new HashMap<>();
    for( int i=0; i < len; i++ ) {
      StringCon name = (StringCon)X.xget();
      TCon      type = (     TCon)X.xget();
      assert !map.containsKey(name);
      map.put(name, type);
    }
    return map;
  }

}
