package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

/**
   Property part
 */
public class PropPart extends Part {
  public final Const.Access _access;
  public TCon _type;
  public Const _init;
  PropPart( Part par, int nFlags, PropCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,null,cond,X);
    int nAccess = X.i8();
    _access = nAccess < 0 ? null : Const.Access.valueOf(nAccess);
    _type = (TCon)X.xget();
    _init = X.xget();
  }

  @Override void link_innards( XEC.ModRepo repo ) {
    _type.link(repo);
    if( _init!=null ) _init.link(repo);
  }
}
