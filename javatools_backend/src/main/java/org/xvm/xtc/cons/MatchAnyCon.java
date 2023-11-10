package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class MatchAnyCon extends Const {
  final Format _f;
  private Const _con;
  private Part _type;
  public MatchAnyCon( CPool X, Const.Format f ) {
    _f = f;
    X.u31();
  }  
  @Override public SB str(SB sb) { return _con.str(sb.p(_f.toString()).p(" -> ")); }
  @Override public void resolve( CPool X ) { _con = X.xget(); }
  //@Override public Part link(XEC.ModRepo repo) {
  //  return _type==null ? (_type = _con.link(repo)) : _type;
  //}
}
