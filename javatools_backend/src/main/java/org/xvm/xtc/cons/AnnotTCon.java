package org.xvm.xtc.cons;

import org.xvm.xtc.CPool;
import org.xvm.xtc.ClassPart;
import org.xvm.util.SB;

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
  public TCon con() { return _con; } // Getter no setter
  @Override public ClassPart clz() {
    return _clz==null ? (_clz = (ClassPart)_an.part()) : _clz;
  }
}
