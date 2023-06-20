package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

import java.util.HashMap;

/**
   Class part
 */
public class ClassPart extends Part {
  private final HashMap<String,TCon> _tcons; // String->Type mapping
  final LitCon _path;           // File name compiling this file
  private final HashMap<String,Part> _parms; // String->Class mapping
  ClassPart( Part par, int nFlags, IdCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,null,cond,X);
    _tcons = parseTypeParms(X);
    _path  = (LitCon)X.xget();
    _parms = _tcons==null ? null : new HashMap<>();
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

  // Tok, kid-specific internal linking.
  @Override void link_innards( XEC.ModRepo repo ) {
    if( _tcons==null ) return;
    for( String name : _tcons.keySet() )
      _parms.put(name,_tcons.get(name).link(repo));
  }

  @Override public Part child(String s, XEC.ModRepo repo) {
    Part kid = super.child(s,repo);
    if( kid!=null ) return kid;
    for( Contrib c : _contribs ) {
      if( (c._comp==Composition.Implements || c._comp==Composition.Extends) &&
          (kid = c.link(repo).child(s,repo)) != null )
        return kid;
    }
    return null;
  }
}
