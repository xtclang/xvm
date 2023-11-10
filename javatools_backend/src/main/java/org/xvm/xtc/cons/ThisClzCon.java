package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.xtc.ClassPart;
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
