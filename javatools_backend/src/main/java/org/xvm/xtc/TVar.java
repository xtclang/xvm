package org.xvm.xtc;

import java.util.HashMap;

// Type variable.  All fields are effectively final for interning.
public class TVar {
  static final HashMap<TVar,TVar> INTERN = new HashMap<>();
  static TVar FREE=null;
  public XType _xt;
  TVar init0(XType xt) { _xt=xt; return this; }
  TVar() {}
  public static TVar make(XType xt) {
    TVar tv = (FREE==null ? new TVar() : FREE).init0(xt);
    TVar tv2 = INTERN.get(tv);
    if( tv2==null ) { FREE=null; INTERN.put(tv,tv); return tv;}
    else { FREE=tv; return tv2; }
  }

  @Override public int hashCode() { return _xt.hashCode(); }
  @Override public boolean equals(Object o) {
    if( this==o ) return true;
    return o instanceof TVar tvar && _xt==tvar._xt;
  }
  @Override public String toString() { return _xt.toString(); }
}
