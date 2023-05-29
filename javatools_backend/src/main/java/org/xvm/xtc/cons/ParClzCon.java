package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class ParClzCon extends PartCon {
  private PartCon _child;
  public ParClzCon( CPool X ) { X.u31();  }
  @Override public void resolve( CPool X ) { _child = (PartCon)X.xget(); }
  @Override public String name() { return _child.name(); }
}
