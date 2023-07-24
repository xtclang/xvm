package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVar;

/**
   Property part
   A property is basically some state (a field in a class) and a getter and setter.
 */
public class PropPart extends Part {
  public final Const.Access _access;
  public TCon _con;
  public Const _init;
  
  // A list of "extra" features about Properties
  public final Contrib[] _contribs;
  
  PropPart( Part par, int nFlags, PropCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,null,cond,X);
    
    // Read the contributions
    _contribs = Contrib.xcontribs(_cslen,X);
    
    int nAccess = X.i8();
    _access = nAccess < 0 ? null : Const.Access.valueOf(nAccess);
    _con = (TCon)X.xget();
    _init = X.xget();
  }

  @Override void link_innards( XEC.ModRepo repo ) {
    // Link all part innards
    _con.link(repo);
    if( _init!=null ) _init.link(repo);
    if( _contribs != null )
      for( Contrib c : _contribs )
        c.link(repo);
  }
  @Override TVar _setype( ) {
    if( _init instanceof TCon tc ) tc.setype();
    return _con.setype();
  }
}
