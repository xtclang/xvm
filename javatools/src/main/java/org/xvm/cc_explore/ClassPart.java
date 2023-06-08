package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

import java.util.HashMap;

/**
   Class component
 */
class ClassPart extends Part {
  final HashMap<StringCon,TCon> _params; // String->Type mapping
  final LitCon _path;           // File name compiling this file
  ClassPart( Part par, int nFlags, IdCon id, CondCon cond, FilePart X ) {
    super(par,nFlags,id,cond,X);
    _params = parseTypeParams(X);
    _path = (LitCon)X.xget();
  }

  // Helper method to read a collection of type parameters.
  HashMap<StringCon, TCon> parseTypeParams(FilePart X) {
    int c = X.u31();
    if( c <= 0 ) return null;

    //ListMap<StringConstant, TypeConstant> map = new ListMap<>();
    //ConstantPool pool = getConstantPool();
    //for (int i = 0; i < c; ++i) {
    //  StringConstant constName = (StringConstant) pool.getConstant(readIndex(in));
    //  TypeConstant   constType = (TypeConstant)   pool.getConstant(readIndex(in));
    //  assert !map.containsKey(constName);
    //  map.put(constName, constType);
    //}
    //return map;
    throw XEC.TODO();
  }

}
