package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;
import org.xvm.cc_explore.tvar.TVLambda;

/**
  Exploring XEC Constants
 */
public class MethodCon extends PartCon {
  public SigCon _sig;
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
    MethodPart meth = (MethodPart)mm.child(name()), meth0=null,meth1=null;
    // Assume if there is one method, it is correct
    if( meth!=null && meth._sibling == null )
      return (_part = meth);
    // Find matching lambda with the easy exact test
    for( MethodPart methx=meth; methx!=null;  methx = methx._sibling )
      if( methx._methcon == this )
        return (_part = methx); // Early out
    // Find matching lambda with signature matching
    int rez0=0, rez1=0;
    for( MethodPart methx=meth; methx!=null;  methx = methx._sibling ) {
      int rez = methx.match_sig_length(_sig);
      if( rez == -1 ) continue;
      if( rez==0 ) { rez0++;  meth0=methx; }
      else         { rez1++;  meth1=methx; }
    }
    if( rez1 >= 1 ) { assert rez1==1;  return (_part = meth1); }
    if( rez0 == 1 )                    return (_part = meth0);

    // Might have to go deep
    if( rez0 > 1 ) {
      // Now try the sig field by field
      _sig.link(repo);          // This can recurse
      rez0=0; rez1=0;
      for( MethodPart methx=meth; methx!=null;  methx = methx._sibling ) {
        int rez = methx.match_sig_length(_sig)==0 ? methx.match_sig(_sig) : -1;
        if( rez == -1 ) continue;
        if( rez==0 ) { rez0++;  meth0=methx; }
        else         { rez1++;  meth1=methx; }
      }
      if( rez1 >= 1 ) { assert rez1==1;  return (_part = meth1); }
      if( rez0 >= 1 ) { assert rez0==1;  return (_part = meth1); }      
    }
    
    // Native methods?
    return mm.addNative();
  }
  
  @Override public String name() { return _par.name(); }
  public TCon[] rawRets () { return _sig.rawRets (); }
  public TCon[] rawParms() { return _sig.rawParms(); }

  @Override TVar _setype() { return _sig.setype(); }
}
