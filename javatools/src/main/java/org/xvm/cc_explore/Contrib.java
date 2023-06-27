package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

import java.util.HashMap;

/**
 * Represents one contribution to the definition of a class. A class (with the term used in the
 * abstract sense, meaning any class, interface, mixin, const, enum, or service) can be composed
 * of any number of contributing components.
 */
public class Contrib {
  final Part.Composition _comp;
  private final TCon _tContrib;
  private final PropCon _prop;
  private final SingleCon _inject;
  private final Annot _annot;
  private final HashMap<String, TCon> _parms;
  // Post link values
  private Part _cPart;
  private final HashMap<String, ClassPart> _clzs;
  
  protected Contrib( CPool X ) {
    _comp = Part.Composition.valueOf(X.u8());
    _tContrib = (TCon)X.xget();
    PropCon prop = null;
    Annot annot = null;
    SingleCon inject = null;
    HashMap<String, TCon> parms = null;
  
    assert _tContrib!=null;
    switch( _comp ) {
    case Extends:
    case Implements:
    case Into:
    case RebasesOnto:
      break;
    case Annotation:
      annot = (Annot)X.xget();
      break;
    case Delegates:
      prop = (PropCon)X.xget();
      break;
      
    case Incorporates:
      int len = X.u31();
      if( len == 0 ) break;
      parms = new HashMap<>();
      for( int i = 0; i < len; i++ ) {
        String name = ((StringCon)X.xget())._str;
        int ix = X.u31();
        parms.put(name, ix == 0 ? null : (TCon)X.get(ix));
      }
      break;

    case Import:
      inject = (SingleCon)X.xget();
      if( inject==null ) break;
      throw XEC.TODO();
      
    default: throw XEC.TODO();
    }
    _annot = annot;
    _prop = prop;
    _inject = inject;
    _parms = parms;
    _clzs = parms==null ? null : new HashMap<>();
  }
  // Link all the internal parts
  Part link( XEC.ModRepo repo ) {
    if( _cPart!=null ) return _cPart;
    _cPart = _tContrib.link(repo);
    
    if( _prop  !=null ) _prop  .link(repo);
    if( _inject!=null ) _inject.link(repo);
    if( _annot !=null ) _annot .link(repo);
    if( _parms !=null )
      for( String name : _parms.keySet() ) {
        TCon tcon = _parms.get(name);
        _clzs.put(name, tcon==null ? null : (ClassPart)tcon.link(repo));
      }
    return _cPart;
  }
  Part part() { assert _cPart!=null; return _cPart; }

  static final Contrib[] xcontribs( int len, CPool X ) {
    if( len==0 ) return null;
    Contrib[] cs = new Contrib[len];
    for( int i=0; i<len; i++ )
      cs[i] = new Contrib( X );
    return cs;
  }

}

