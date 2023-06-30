package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.*;
import org.xvm.cc_explore.util.S;

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
      if( _name2kid.get(s) instanceof TDefPart ) {
        stv.add_fld(s,new TVIsa(new TVLeaf())); // Add a local typdef as a ISA
      } else if( _name2kid.get(s) instanceof PropPart pp ) {
        // Property.  
        if( _tcons!=null && _tcons.get(s)!=null ) {
          stv.add_fld(s,new TVIsa(new TVLeaf()));

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
      stv = (TVStruct)stv.find();
    }

    // This Class may extend another one.
    // Look for extends, implements, etc. contributions
    if( _contribs != null ) {
      for( Contrib c : _contribs ) {
        c.link( repo );
        switch( c._comp ) {
        case Extends -> {
          assert _contribs[0] == c; // Can optimize if extends are always in slot 0
          // Add contrib class to super chain
          throw XEC.TODO();
        }
        case Implements, Delegates -> {
          TermTCon tc;
          if( c._tContrib instanceof TermTCon tc2 ) tc = tc2;
          else if( c._tContrib instanceof ImmutTCon itc )
            throw XEC.TODO(); //tc = (TermTCon)itc.icon(); // Wrap in immut
          else throw XEC.TODO(); // Some other thing here?
          // Unify interface into class
          assert tc.clz()._f==Part.Format.INTERFACE;
          assert ((TVStruct)tc.tvar()).is_open();
          tc.tvar().fresh_unify(tvar(),null);
        }

        // This is a "mixin", marker interface.  It becomes part of the parent-
        // chain of the following "linked list" of classes.  The linked list
        // is formed from a left-spline UnionTCon.
        case Into -> {
          TCon mixed = c._tContrib;
          while( mixed instanceof UnionTCon utc ) {
            mixed = utc.con1(); // Next
            TermTCon tc = (TermTCon)utc.con2();
            tc.setype(repo);
            ClassPart clz = tc.clz();
            //clz.env_extend( this );
            throw XEC.TODO();
          }
          TermTCon tc = (TermTCon)mixed;
          tc.setype(repo);
          ClassPart clz = tc.clz();
          //clz.env_extend( this );
          throw XEC.TODO();
        }
        }
      }
    }

    // All other contributions
    if( _contribs != null ) {
      for( Contrib c : _contribs ) {
        switch( c._comp ) {
        case Extends, Implements, Delegates, Into:
          break;                // Already did
        default:
          // Handle other contributions
          throw XEC.TODO();
        }
      }
    }

    // The structure has more unspecified fields, or not.
    // Classes are closed: all fields are listed.
    // Interfaces are open: at least these fields, but you are allowed more.
    switch( _f ) {
    case CLASS, CONST, MODULE, PACKAGE: stvar().close(); break;
    case INTERFACE: break;
    default: throw XEC.TODO();
    };

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
