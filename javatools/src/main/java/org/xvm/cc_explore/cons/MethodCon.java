package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;

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
    // Find the child in the parent
    MethodPart meth = (MethodPart)mm._name2kid.get(name());
    // Confirm the signature matches
    //throw XEC.TODO();
    // Note that if there are no siblings, we do not check the method id - and
    // indeed this can mismatch
    if( meth._sibling!=null ) // Search sibling list
      //while( meth._id!=this ) meth = meth._sibling;
      throw XEC.TODO();
    return (_part = meth);
  }
  @Override public String name() { return _par.name(); }
  public TCon[] rawRets () { return _sig.rawRets (); }
  public TCon[] rawParms() { return _sig.rawParms(); }
}
