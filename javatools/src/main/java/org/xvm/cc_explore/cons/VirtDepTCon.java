package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import static org.xvm.cc_explore.Part.Composition.*;

/**
  Exploring XEC Constants
 */
public class VirtDepTCon extends DepTCon {
  private final boolean _thisClz;
  private String _name;
  public VirtDepTCon( CPool X ) {
    super(X);
    X.u31();
    _thisClz = X.u1();
  }
  @Override public SB str(SB sb) { return super.str(sb.p(_name)); }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    _name =((StringCon)X.xget())._str;
  }
  @Override public Part link(XEC.ModRepo repo) {
    if( _part!=null ) return _part;
    ClassPart par = (ClassPart)_par.link(repo);
    _part = search(par,null);
    assert _part!=null;
    return _part;
  }

  // Hunt this clz for name, plus recursively any Implements contribs.
  // Assert only 1 found
  Part search( ClassPart clz, Part p ) {
    Part p0 = clz.child(_name);
    if( p0!=null ) {
      assert p==null || p==p0;
      return p0;
    }
    if( clz._contribs != null )
      for( Contrib c : clz._contribs )
        if( c._comp==Implements || c._comp==Into || c._comp==Extends )
          p = search(((ClzCon)c._tContrib).clz(),p);
    return p;
  }
  
  
}
