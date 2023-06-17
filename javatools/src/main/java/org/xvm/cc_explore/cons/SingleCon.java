package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class SingleCon extends Const {
  final Format _f;
  PartCon _con;                 // Name for the wrapped part
  Part _part;                    // The actual part
  public SingleCon( CPool X, Format f ) {
    _f = f;
    X.u31();
  }
  @Override public void resolve( CPool X ) { _con = (PartCon)X.xget(); }
  @Override public Part link( XEC.ModRepo repo ) {
    return _part==null ? (_part=_con.link(repo)) : _part;
  }
}
