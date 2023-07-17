package org.xvm.cc_explore.tvar;

import org.xvm.cc_explore.util.*;
import org.xvm.cc_explore.XEC;

/** A lambda, higher-order function
 *
 */
public class TVLambda extends TVar {
  public final int _nargs;
  
  public TVLambda( int nargs, int nrets ) {
    super(new TVar[nargs+nrets]);
    _nargs = nargs;
    // Slots 0- (nargs-1) for args
    // Slots nargs - length for returns
    for( int i=0; i<_args.length; i++ )
      _args[i] = new TVLeaf();
  }

  // -------------------------------------------------------------
  @Override public void _union_impl( TVar tv3) { }

  @Override int _unify_impl(TVar that ) {
    TVar thsi = this;
    assert _nargs == ((TVLambda)that)._nargs;
    assert _args.length == that._args.length;
    int i;
    for( i=0; i<_nargs; i++ ) {
      thsi.arg( i )._unify( that.arg( i ));
      thsi = thsi.find();
      that = that.find();      
    }
    return 0;
  }

  // -------------------------------------------------------------
  // Sub-classes specify trial_unify on sub-parts.
  // Check arguments, not return nor omem.
  @Override boolean _trial_unify_ok_impl( TVar tv3 ) {
    TVLambda that = (TVLambda)tv3; // Invariant when called
    if( _nargs != that._nargs ) return false; // Fails to be equal
    // Check all other arguments
    for( int i=0; i<_args.length; i++ )
      if( !arg(i)._trial_unify_ok(that.arg(i)) )
        return false;           // Arg failed so trial fails
    return true;
  }

  @Override SB _str_impl(SB sb, VBitSet visit, VBitSet dups, boolean debug) {
    sb.p("{ ");
    for( int i=0; i<_nargs; i++ )
      _args[i]._str(sb,visit,dups,debug).p(' ');
    sb.p("-> ");
    for( int i=_nargs; i<_args.length; i++ )
      _args[i]._str(sb,visit,dups,debug).p(' ');
    return sb.p("}");
  }

}
