package org.xvm.xec.ecstasy.annotations;

import org.xvm.xec.XTC;
import org.xvm.xrun.Never;


public class FutureVar<Referent extends XTC> extends XTC {
  public FutureVar(Never n) {}
  public FutureVar() {}
  private Referent _ref;
  public Referent $get() { return _ref; }
  public void $set(Referent ref) { _ref=ref; }
}
