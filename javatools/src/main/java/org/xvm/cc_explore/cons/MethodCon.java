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
    MethodPart meth = (MethodPart)mm.child(name(),repo);
    //TVLambda lam0 = (TVLambda)_sig.setype(null);
    // Find matching lambda
    for( ; meth!=null;  meth = meth._sibling )
      if( meth._methcon == this )
        break;
    return (_part = meth);
  }
  
  @Override public Part part() {
    assert _sig.has_tvar();  // Requires setypes, and signature disambiguation 
    return super.part();
  }
  @Override public String name() { return _par.name(); }
  public TCon[] rawRets () { return _sig.rawRets (); }
  public TCon[] rawParms() { return _sig.rawParms(); }

  @Override TVar _setype( XEC.ModRepo repo ) {
    throw XEC.TODO();
  }
}
