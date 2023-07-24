package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.*;
import org.xvm.cc_explore.util.SB;

import java.util.HashMap;

/**
 * Represents one contribution to the definition of a class. A class (with the term used in the
 * abstract sense, meaning any class, interface, mixin, const, enum, or service) can be composed
 * of any number of contributing components.
 */
public class Contrib {
  public final Part.Composition _comp;
  private boolean _linked;
  private final PropCon _prop;
  private final SingleCon _inject;
  private final Annot _annot;
  private final HashMap<String, TCon> _parms;
  // Post link values
  final HashMap<String, ClassPart> _clzs;
  
  public final TCon _tContrib;
  private TVar _tvar;
  
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
  void link( XEC.ModRepo repo ) {
    if( _linked ) return;
    _linked = true;
    _tContrib.link(repo);
    
    if( _prop  !=null ) _prop  .link(repo);
    if( _inject!=null ) throw XEC.TODO(); // _inject.link(repo);
    if( _annot !=null ) _annot .link(repo);
    if( _parms !=null )
      for( String name : _parms.keySet() ) {
        TCon tcon = _parms.get(name);
        if( tcon!=null ) tcon.link(repo);
        _clzs.put(name,tcon==null ? null : ((ClzCon)tcon).clz());
      }
  }

  public TVar tvar() { assert _tvar!=null; return _tvar; }
  public final TVar setype( ) { return _tvar==null ? (_tvar = _tContrib.setype()) : tvar(); }
  
  
  @Override public String toString() { return str(new SB()).toString(); }
  public SB str(SB sb) {
    sb.p(_comp.toString());
    return _tContrib ==null ? sb : _tContrib.str(sb.p(" -> "));
  }
  
  static Contrib[] xcontribs( int len, CPool X ) {
    if( len==0 ) return null;
    Contrib[] cs = new Contrib[len];
    for( int i=0; i<len; i++ )
      cs[i] = new Contrib( X );
    return cs;
  }

}
