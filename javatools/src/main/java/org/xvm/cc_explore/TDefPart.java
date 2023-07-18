package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVar;

/**
   Type Definition part
 */
// TODO: Probably not a Part
public class TDefPart extends Part {
  public final TCon _type;
  TDefPart( Part par, int nFlags, TDefCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,null,cond,X);
    _type = (TCon)X.xget();
  }
  
  // Tok, kid-specific internal linking.
  @Override void link_innards( XEC.ModRepo repo ) { _type.link(repo); }
  @Override TVar _setype( ) { return _type.setype(); }
}
