package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class SingleCon extends PartCon {
  final Format _f;
  public SingleCon( CPool X, Format f ) {
    _f = f;
    X.u31();
  }
  @Override public String name() { throw XEC.TODO(); }
  @Override public void resolve( CPool X ) { _par = (PartCon)X.xget(); }
  @Override public Part link( XEC.ModRepo repo ) {
    return _part==null ? (_part=_par.link(repo)) : _part;
  }
}
