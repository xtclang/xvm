package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;

/**
  Exploring XEC Constants
 */
public class SingleCon extends PartCon {
  final Format _f;
  public SingleCon( CPool X, Format f ) {
    X.u31();
    _f = f;
  }
  @Override public String name() { throw XEC.TODO(); }
  @Override public void resolve( CPool X ) { _par = (PartCon)X.xget(); }
  @Override Part _part() { return _par.part(); }
}
