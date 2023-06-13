package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

/**
   Type Definition part
 */
class TDefPart extends Part<TDefCon> {
  public final TCon _type;
  TDefPart( Part par, int nFlags, TDefCon id, CondCon cond, FilePart X ) {
    super(par,nFlags,id,cond,X);
    _type = (TCon)X.xget();
  }
  
}
