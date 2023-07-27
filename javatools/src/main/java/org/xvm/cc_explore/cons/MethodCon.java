package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;
import org.xvm.cc_explore.tvar.TVLambda;

/**
  Exploring XEC Constants
 */
public class MethodCon extends PartCon {
  private SigCon _sig;
  public final int _lamidx;
  public MethodCon( CPool X ) {
    X.u31();                    // Parent
    X.u31();                    // Signature
    _lamidx = X.u31();          // Lambda
  }
  @Override public SB str(SB sb) { return super.str(sb.p(_sig._name)); }
  @Override public void resolve( CPool X ) {
    _par = (MMethodCon)X.xget();
    _sig = (SigCon)X.xget();
    assert _lamidx==0 || ((MMethodCon)_par)._name.equals("->");
  }
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    // Link the parent, do any replacement lookups
    MMethodPart mm = (MMethodPart)_par.link(repo).link(repo);
    // Find a child in the parent
    MethodPart meth = (MethodPart)mm.child(name()), meth0 = meth;
    // Find matching lambda
    for( ; meth0!=null;  meth0 = meth0._sibling )
      if( meth0._methcon == this || meth0.isSynthetic() )
        break;
    if( meth0==null && meth._sibling==null )
      meth0 = meth;             // Only one choice, take it
    assert meth0!=null;
    return (_part = meth0);
  }
  
  @Override public String name() { return _par.name(); }
  public TCon[] rawRets () { return _sig.rawRets (); }
  public TCon[] rawParms() { return _sig.rawParms(); }

  @Override TVar _setype() { return _sig.setype(); }
}
