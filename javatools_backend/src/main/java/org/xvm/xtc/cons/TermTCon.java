package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon implements ClzCon {
  Const _id;
  private Part _part;
  public TermTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) { return _id.str(sb.p("# -> ")); }
  @Override public ClassPart clz() {
    Part p = part();
    return p instanceof ClassPart clz ? clz : null;
  }
  @Override public void resolve( CPool X ) { _id = X.xget(); }
  public Const id() { return _id; }
  public String name() { return ((IdCon)_id).name(); }

  @Override public Part part() {
    return _part==null ? (_part = _id.part()) : _part;
  }
}
