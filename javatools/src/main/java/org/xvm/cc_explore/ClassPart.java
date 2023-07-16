package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.*;
import org.xvm.cc_explore.util.S;

import java.util.Arrays;
import java.util.HashMap;

/**
   Class part


class - closed-struct with methods as fields; FIDXs on concrete methods
interface, delegates - defined as an open-struct with methods as fields
interface abstract method is a leaf (or a lambda leaf with arg counts).  No FIDX.
interface concrete method is a full lambda with FIDX.
"class implements iface" - unify the open iface struct against closed class struct
"class extends class" - chain thru a special field " super".  
Special type constructor "isa X".
_tcons add a field their name to the class, pts to a ISA tvar.
Can drop the env lookup I think.
Methods may have a special arg0, also of ISA TVar.


 */
public class ClassPart extends Part {
  private final HashMap<String,TCon> _tcons; // String->TCon mapping
  final LitCon _path;           // File name compiling this file
  final Part.Format _f;         // Class, Interface, Mixin, Enum, Module, Package

  ClassPart _super; // Super-class.  Note that "_par" field is the containing Package, not the superclass

  ClassPart[] _mixes;             // List of incorporated mixins.

  
  // A list of "extra" features about Classes: extends, implements, delegates
  public final Contrib[] _contribs;
  
  ClassPart( Part par, int nFlags, IdCon id, CondCon cond, CPool X, Part.Format f ) {
    super(par,nFlags,id,null,cond,X);

    _f = f;
    _contribs = Contrib.xcontribs(_cslen,X);
    
    _tcons = parseTypeParms(X);
    _path  = (LitCon)X.xget();
  }

  // Helper method to read a collection of type parameters.
  HashMap<String,TCon> parseTypeParms( CPool X ) {
    int len = X.u31();
    if( len <= 0 ) return null;
    HashMap<String, TCon> map = new HashMap<>();
    for( int i=0; i < len; i++ ) {
      String name = ((StringCon)X.xget())._str;
      TCon   type =  (     TCon)X.xget();
      TCon old = map.put(name, type);
      assert old==null;         // No double puts
    }
    return map;
  }

  // Tok, kid-specific internal linking.
  @Override void link_innards( XEC.ModRepo repo ) {
    // Make the class struct, with fields
    TVStruct stv = new TVStruct(_name,true);
    set_tvar(stv);              // Set the local type to stop recursions
    for( String s : _name2kid.keySet() ) {
      Part p = _name2kid.get(s);
      if( p instanceof TDefPart ) {
        stv.add_fld((_name+".typedef."+s).intern(),new TVLeaf()); // Add a local typdef as a ISA
      } else if( p instanceof PropPart pp ) {
        // Property.  
        if( _tcons!=null && _tcons.get(s)!=null ) {
          stv.add_fld(generic(s),new TVLeaf());

        } else {
          // This is an XTC "property" - a field with built-in getters & setters.
          // These built-in fields have mangled names.
          // TODO: Already specify get:{-> Leaf} and set:{ Leaf -> }
          stv.add_fld((s+".get").intern(),new TVLeaf());
          stv.add_fld((s+".set").intern(),new TVLeaf());
        }
      } else {
        stv.add_fld(s,new TVLeaf());
      }
      assert !stv.unified();
    }

    // This Class may extend another one.
    // Look for extends, implements, etc. contributions
    if( _contribs != null ) {
      for( Contrib c : _contribs ) {
        c.link( repo );
        switch( c._comp ) {
        case Extends -> {
          assert _contribs[0] == c; // Can optimize if extends are always in slot 0
          TermTCon ttc = ifaces0(c._tContrib); // The class
          Format f = ttc.clz()._f;
          assert f==Part.Format.CONST || f==Part.Format.ENUM; // Class or Constant or Enum class
          // Cannot assert struct is closed, because of recursive references.
          _super = ttc.clz();   // Record super-class
          if( c._clzs != null )  throw XEC.TODO();
        }
        case Implements, Delegates -> {
          TermTCon ttc = ifaces0(c._tContrib); // The iface, perhaps after a TVImmut
          assert ttc.clz()._f==Part.Format.INTERFACE;
          assert ((TVStruct)ttc.tvar()).is_open();
          // Unify interface into class
          c.tvar().fresh_unify(tvar(),null);
          if( c._clzs != null )  throw XEC.TODO();
        }

        // This is a "mixin", marker interface.  It becomes part of the parent-
        // chain of the following "linked list" of classes.  The linked list is
        // formed from a left-spline UnionTCon.  Proper mixins was confirmed by
        // XTC compiler, and TODO Some Day we can verify again here.
        case Into -> { }

        // This can be a mixin marker annotation, which has been checked by the
        // XTC compiler already.
        case Annotation -> {
          ClassPart mix = ifaces0(c._tContrib).clz();
          assert mix._f==Part.Format.MIXIN;
          assert mix._contribs[0]._comp==Part.Composition.Into;
        }
        
        case Incorporates -> {
          ClassPart mix = ifaces0(c._tContrib).clz();
          if( _mixes==null ) _mixes = new ClassPart[1];
          else _mixes = Arrays.copyOfRange(_mixes,0,_mixes.length+1);
          _mixes[_mixes.length-1] = mix;
          // Unify a fresh copy of the mixin's type
          mix.tvar().fresh_unify(tvar(),null);
          // Set generic parameter types
          if( c._clzs != null )
            for( String generic : c._clzs.keySet() )
              ((TVStruct)tvar()).arg(mix.generic(generic)).unify( c._clzs.get( generic ).tvar() );
        }

        default ->  // Handle other contributions
          throw XEC.TODO();
        }
        
      }
    }

    // The structure has more unspecified fields, or not.
    // Interfaces are open: at least these fields, but you are allowed more.
    switch( _f ) {
    case CLASS, CONST, MODULE, PACKAGE, ENUM, ENUMVALUE: stvar().close(); break;
    case INTERFACE, MIXIN: break;
    default: throw XEC.TODO();
    };
  }

  private static TermTCon ifaces0( TCon tc ) {
    if( tc instanceof TermTCon ttc ) return ttc;
    if( tc instanceof ImmutTCon itc ) return (TermTCon)itc.icon();
    if( tc instanceof ParamTCon ptc ) return (TermTCon)ptc._con;
    throw XEC.TODO();
  }  

  // Mangle a generic type name
  private String generic( String s ) {
    return (_name+".generic."+s).intern();
  }

  
  @Override public Part child(String s, XEC.ModRepo repo) {
    Part kid = super.child(s,repo);
    if( kid!=null ) return kid;
    for( Contrib c : _contribs ) {
      if( (c._comp==Composition.Implements || c._comp==Composition.Extends) ) {
        c.link(repo);
        //if( (kid = c.child(s,repo)) != null )
        //  return kid;
        throw XEC.TODO();       // Lookup child in contrib
      }
    }
    return null;
  }

  TVStruct stvar() { return (TVStruct)tvar(); }
}
