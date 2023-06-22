package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class AnnotTCon extends TCon {
  Annot _an;
  private TCon _con;
  private ClassPart _clz;
  public AnnotTCon( CPool X ) {
    X.u31();
    X.u31();
  }
  @Override public SB str(SB sb) {
    return _con.str(sb.p("@").p(_an._con._name).p(" -> "));
  }
  @Override public void resolve( CPool X ) {
    _an = (Annot)X.xget();
    _con = (TCon)X.xget();
  }
  @Override public ClassPart link(XEC.ModRepo repo) {
    if( _clz!=null ) return _clz;
    return (_clz = (ClassPart)_con.link(repo));
  }
}
