package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

/**
   Type Definition part
 */
public class TDefPart extends Part {
  public final TCon _type;
  private Part _part;
  TDefPart( Part par, int nFlags, TDefCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,null,cond,X);
    _type = (TCon)X.xget();
  }
  
  // Tok, kid-specific internal linking.
  @Override void link_innards( XEC.ModRepo repo ) {
    _part = _type.link(repo);
  }  
}
