package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.*;
import org.xvm.util.S;

/**
   Property part
   A property is basically some state (a field in a class) and a getter and setter.
   Also used for generic parameterized types:
      "mixin NumberArray<Element extends Number>"
   Here "Element" is a PropCon/PropPart
 */
public class PropPart extends Part {
  public final Const.Access _access;
  public TCon _con;
  public Const _init;
  public short _order;          // Order of properties in sythentic methods for compares
  
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

  @Override public Part child(String s) {
    Part p = super.child(s);
    if( p!=null ) return p;
    assert _par instanceof ClassPart;
    // Generic parameters are matched here, and re-do the lookup in the generic
    // type parameter's base type.
    TermTCon genttc = _con.is_generic();
    if( genttc!=null ) 
      return genttc.clz().child(s);
    // Things like "get" and "set"
    MMethodPart mm = new MMethodPart(this,s);
    return mm.addNative();
  }

  @Override void link_innards( XEC.ModRepo repo ) { }

  // Terrible name.  Means "this property is a Real(tm) Field", and it
  // shows up in the default hashCode, equals, and compare.
  public boolean isField() {
    // No type fields
    if( isAuxiliary() ) return false;
    // No equals on static props
    if( isStatic() ) return false;
    // No equals on get-only properties
    if( _name2kid != null && (_name2kid.containsKey("get") || _name2kid.containsKey("->")) )
      return false;
    return true;
  }
}
