package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class DecClzCon extends Const implements IdCon {
  private TCon _type;
  public DecClzCon( CPool X ) { X.u31(); }
  @Override public String name() { throw XEC.TODO(); }
  @Override public void resolve( CPool X ) { _type = (TCon)X.xget(); }
  @Override public Part link(XEC.ModRepo repo) { throw XEC.TODO(); }
}
