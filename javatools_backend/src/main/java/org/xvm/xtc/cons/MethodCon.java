package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class MethodCon extends PartCon {
  public SigCon _sig;
  public final int _lamidx;
  private boolean _link_start;
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

  @Override Part _part() {
    MMethodPart mm = (MMethodPart)_par.part();
    
    // Find first child in the parent, head of a linked list of choices
    MethodPart meth = (MethodPart)mm.child(name());
    assert meth!=null;

    // Assume if there is one method, it is correct
    if( meth._sibling == null )
      return meth;
    
    // Find matching lambda with the easy exact test
    for( MethodPart methx=meth; methx!=null;  methx = methx._sibling )
      if( methx._methcon == this )
        return methx; // Early out
    
    // Need to do a proper instanceof test
    MethodPart rez=null;
    for( MethodPart methx=meth; methx!=null;  methx = methx._sibling ) {
      if( methx.match_sig(_sig) ) {
        assert rez==null;       // Ambiguous
        rez = methx;
      }
    }
    assert rez != null;
    return rez;
  }
  
  @Override public String name() { return _par.name(); }
  public TCon[] rawRets () { return _sig.rawRets (); }
  public TCon[] rawParms() { return _sig.rawParms(); }
}
