package org.xvm.cons;

import org.xvm.*;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon implements ClzCon {
  Const _id;
  private Part _part;
  public TermTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) { return _id.str(sb.p("# -> ")); }
  @Override public ClassPart clz() { return (ClassPart)_part; }
  @Override public void resolve( CPool X ) { _id = X.xget(); }
  public Const id() { return _id; }
  public String name() { return ((IdCon)_id).name(); }
  
  @Override public Part link(XEC.ModRepo repo) {
    return _part==null ? (_part=_id.link(repo)) : _part;
  }
  // After linking, the part call does not need the repo.
  @Override public Part part() { return _part;  }

  @Override int _eq( TCon tc ) {
    TermTCon ttc = (TermTCon)tc; // Invariant when called
    assert _part!=null && ttc._part!=null;
    return _part == ttc._part ? 1 : -1;
  }
}
