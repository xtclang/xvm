package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;

/**
  Exploring XEC Constants
 */
public class AnnotTCon extends TCon implements ClzCon {
  Annot _an;
  private TCon _con;
  private ClassPart _clz;
  public AnnotTCon( CPool X ) {
    X.u31();
    X.u31();
  }
  @Override public SB str(SB sb) {
    return _con.str(sb.p("@").p(_an._par.name()).p(" -> "));
  }
  @Override public void resolve( CPool X ) {
    _an = (Annot)X.xget();
    _con = (TCon)X.xget();
  }
  @Override public ClassPart link(XEC.ModRepo repo) {
    if( _clz!=null ) return _clz;
    if( _an != null ) _an.link(repo);
    return (_clz = (ClassPart)_con.link(repo));
  }
  public TCon con() { return _con; } // Getter no setter
  @Override public ClassPart clz() { return _clz; }
  @Override TVar _setype() {
    _an.setype( );
    return _con.setype();
  }
}
