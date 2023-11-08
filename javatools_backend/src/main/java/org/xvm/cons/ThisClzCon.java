package org.xvm.cons;

import org.xvm.*;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class ThisClzCon extends PartCon {
  public ThisClzCon( CPool X ) { X.u31();  }
  @Override public SB str(SB sb) { return super.str(sb.p("this")); }
  @Override public void resolve( CPool X ) { _par = (ClassCon)X.xget(); }
  @Override public String name() { return _par.name(); }
  @Override public ClassPart link( XEC.ModRepo repo ) {
    return (ClassPart)(_part==null ? (_part=_par.link(repo)) : _part);
  }
}
