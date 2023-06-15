package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

/**
   Type Definition part
 */
class TDefPart extends Part {
  public final TCon _type;
  TDefPart( Part par, int nFlags, TDefCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,cond,X);
    _type = (TCon)X.xget();
  }
  
}
