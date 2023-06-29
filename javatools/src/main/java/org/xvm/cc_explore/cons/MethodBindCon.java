package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class MethodBindCon extends PartCon {
  public MethodBindCon( CPool X ) { X.u31(); }
  @Override public String name() { throw XEC.TODO(); }
  @Override public void resolve( CPool X ) { _par = (MethodCon)X.xget(); }
  @Override public Part link(XEC.ModRepo repo) {
    return _part==null ? (_part=(MethodPart)_par.link(repo)) : _part;
  }
}
