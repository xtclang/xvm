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
  
  ClassPart( Part par, int nFlags, Const id, CondCon cond, CPool X, Part.Format f ) {
    super(par,nFlags,id,null,cond,X);

    _f = f;
    _contribs = Contrib.xcontribs(_cslen,X);
    
    _tcons = parseTypeParms(X);
    _path  = (LitCon)X.xget();
  }
   
  // Constructed class parts
  ClassPart( Part par, String name, Part.Format f ) {
    super(par,name);
    _f = f;
    _tcons = null;
    _path = null;
    _contribs = null;
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
    // This Class may extend another one.
    // Look for extends, implements, etc. contributions
    if( _contribs != null )
      for( Contrib c : _contribs )
        c.link( repo );
  }

  // Mangle a generic type name
  private String generic( String s ) {
    return (_name+".generic."+s).intern();
  }
  
  @Override TVar _setype() {
    // Make the class struct, with fields
    TVStruct stv = new TVStruct(_name,true);
    setype_stop_cycles( stv );
    if( _name2kid != null )
      for( String s : _name2kid.keySet() ) {
        Part p = _name2kid.get(s);
        if( p instanceof TDefPart ) {
          stv.add_fld((_name+".typedef."+s).intern()); // Add a local typdef as a ISA
        } else if( p instanceof PropPart pp ) {
          // Property.  
          if( _tcons!=null && _tcons.get(s)!=null ) {
            stv.add_fld(generic(s));
    
          } else {
            // This is an XTC "property" - a field with built-in getters & setters.
            // These built-in fields have mangled names.
            // TODO: Already specify get:{-> Leaf} and set:{ Leaf -> }
            stv.add_fld((s+".get").intern());
            stv.add_fld((s+".set").intern());
          }
        } else {
          stv.add_fld(s);
        }
      }

    if( _contribs != null ) {
      for( Contrib c : _contribs ) {
        switch( c._comp ) {
        case Extends -> {
          _super = ((ClzCon)c._tContrib).clz();
          assert _super._f==Format.CLASS
            || _super._f==Format.CONST
            || _super._f==Format.ENUM
            || _super._f==Format.SERVICE
            || _super._f==Format.MIXIN; 
          // Cannot assert struct is closed, because of recursive references.
          if( c._clzs != null )  throw XEC.TODO();
        }
        case Implements, Delegates -> {
          assert ((ClzCon)c._tContrib).clz()._f==Part.Format.INTERFACE;
          TVStruct ctv = (TVStruct)c.setype();
          assert ctv.is_open();
          // Unify interface into class
          ctv.fresh_unify(stv,null);
          assert !stv.unified();
          if( c._clzs != null )  throw XEC.TODO();
        }
  
        // This is a "mixin", marker interface.  It becomes part of the parent-
        // chain of the following "linked list" of classes.  The linked list is
        // formed from a left-spline UnionTCon.  Proper mixins was confirmed by
        // XTC compiler, and TODO Some Day we can verify again here.
        case Into -> { }
  
        case Incorporates, Annotation -> {
          ClassPart mix = ((ClzCon)c._tContrib).clz();
          if( _mixes==null ) _mixes = new ClassPart[1];
          else _mixes = Arrays.copyOfRange(_mixes,0,_mixes.length+1);
          _mixes[_mixes.length-1] = mix;
          // Unify a fresh copy of the mixin's type
          mix.setype().fresh_unify(stv,null); // Self picks up mixin fields
          assert !stv.unified();
          // Set generic parameter types
          if( c._clzs != null )
            for( String generic : c._clzs.keySet() ) {
              TVar mix_tv = stv.arg(generic(generic)); // stv is the self, picking up the mixin
              if( mix_tv!=null ) {
                Part pgen = c._clzs.get(generic);
                if( pgen!=null )
                  pgen.tvar().fresh_unify(mix_tv,null);
              }
            }
        }

        case Import -> {
          TVar tvi = c.setype();
          tvi.unify(stv);
          stv = stvar();
        }
        
        default ->  // Handle other contributions
          throw XEC.TODO();
        }        
      }
    }

    // The structure has more unspecified fields, or not.
    // Interfaces are open: at least these fields, but you are allowed more.
    switch( _f ) {
    case CLASS, CONST, MODULE, PACKAGE, ENUM, ENUMVALUE, SERVICE: stv.close(); break;
    case INTERFACE, MIXIN: break;
    default: throw XEC.TODO();
    };

    return stv;
  }

  TVStruct stvar() { return (TVStruct)tvar(); }
}
