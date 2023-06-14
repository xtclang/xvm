package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

/**
   Property part
 */
class PropPart extends Part {
  public final Const.Access _access;
  public final TCon _type;
  public final Const _init;
  PropPart( Part par, int nFlags, PropCon id, CondCon cond, FilePart X ) {
    super(par,nFlags,id,cond,X);
    int nAccess = X.i8();
    _access = nAccess < 0 ? null : Const.Access.valueOf(nAccess);
    _type = (TCon)X.xget();
    _init = X.xget();
  }  
}
