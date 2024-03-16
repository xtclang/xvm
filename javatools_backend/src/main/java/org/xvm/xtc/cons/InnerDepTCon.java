package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.xtc.ClassPart;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class InnerDepTCon extends DepTCon {
  private ClassCon _child;
  public InnerDepTCon( CPool X ) {
    super(X);
    X.u31();
  }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    _child = (ClassCon)X.xget();
  }
  @Override ClassPart _part() { throw XEC.TODO(); }
}
