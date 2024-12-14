package org.xvm.xtc;

import java.util.Arrays;
import java.util.HashMap;
import org.xvm.XEC;
import org.xvm.util.S;
import org.xvm.util.SB;

// Type variable.  All fields are effectively final for interning.
public class TVarZ extends TVar {
  // All visible type variables are present in the XClz, whether defined locally or not.
  // All type variables have the local name they got declared with.
  // If the variable is local, its _def field is null.
  // Otherwise the _def is a super, interface or outer class.
  // If this has been bound/unified to another type variable, its local name is included.

  // Example: interface IFace<Elem>;  class Base<Key> implements IFace<Key>;
  // IFace : {"Elem",null , -   } // Elem is local to IFace
  // Base  : {"Key" ,null , -   } // Key  is local to Base
  // Base  : {"Elem",IFace,"Key"} // Elem is local to IFace, tied to local Key


  static final HashMap<TVarZ,TVarZ> INTERN = new HashMap<>();
  static TVarZ FREE=new TVarZ();
  // Type variable name from owner's POV.
  // Renames just map from this one to some owners name.
  // Simple restrictions keep the same name.
  String _name;             // Type variable name from owner's POV

  // Where does this TVar come from?  null is local/self.
  // Otherwise this is a super, interface, or outer class.
  XClz _def;

  // Unified with local TVar
  String _local;


  private TVarZ init1(XType xt, String name, XClz def, String local) {
    init0(xt);
    _name = name;
    _def  = def;
    _local= local;
    return this;
  }
  TVarZ() {super();}
  static TVarZ make( XType xt, String name ) { return make(xt,name,null,null); }
  static TVarZ make( XType xt, String name, XClz def, String local ) {
    TVarZ tv = FREE.init1(xt,name,def,local);
    TVarZ tv2 = INTERN.get(tv);
    if( tv2!=null ) return tv2;
    FREE = new TVarZ();
    INTERN.put(tv,tv);
    return tv;
  }

  // Refine xt
  TVarZ makeRefine( XType xt ) {
    return xt==_xt ? this : make(xt,_name,_def,_local);
  }

  // Local type - either no remote def, or overriding same name remote def
  public boolean local() {
    return _def==null || S.eq(_name,_local);
  }

  // Generic Def:
  boolean genericDef(XClz supr) {
    // If local defined
    return _def==null ||
      // If renamed from super (ignoring renames from ifaces)
      (_local!=null && supr!=null) /*||
      // If local to super but not local to self, will use concrete type
      (_def==supr && supr.tvar(_name).local())*/;
  }

  // Generic Use: show TVarZ if renamed in base
  boolean genericUse(XClz base) {
    return _def==base && (_local!=null /*|| supr.tvar(_name).local()*/);
  }

  boolean protoDef(XClz supr) {
    // If local defined
    return _def==null ||
      // If renamed from super (ignoring renames from ifaces)
      (_local!=null && supr!=null) ||
      // If local to super but not local to self, will use concrete type
      (_def==supr && supr.tvar(_name).local());
  }


  @Override public int hashCode() {
    return S.fold(S.rot(_xt.hashCode(),7)
                  ^ S.rot(_name.hashCode(),13)
                  ^ (_def  ==null ? 0 : S.rot(_def  .hashCode(),17))
                  ^ (_local==null ? 0 : S.rot(_local.hashCode(),19)));
  }
  @Override public boolean equals(Object o) {
    if( this==o ) return true;
    if( !(o instanceof TVarZ tv) ) return false;
    return _xt==tv._xt && S.eq(_name,tv._name) && _def==tv._def && S.eq(_local,tv._local);
  }
  @Override public final String toString() { return str(new SB()).toString(); }
  SB str( SB sb ) {
    if( _def!=null )
      sb.p(_def.name()).p(".");
    sb.p(_name).p(":");
    _xt.str(sb);
    if( _local!=null )
      sb.p("=>").p(_local);
    return sb;
  }
}
