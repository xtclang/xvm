package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.*;

import java.util.HashMap;

/**
   Class part
 */
public class ClassPart extends Part {
  private final HashMap<String,TCon> _tcons; // String->TCon mapping
  final LitCon _path;           // File name compiling this file

  // A list of "extra" features about Classes: extends, implements, delegates
  public final Contrib[] _contribs;
  
  ClassPart( Part par, int nFlags, IdCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,null,cond,X);

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
    // Set the local type to stop recursions
    set_tvar(new TVStruct(_name,true));

    
    // The class is generified/parameterized.  The _tcons list the parameter
    // names.  So in the class "Iterator<Element>", "Element" is in the tcons.
    if( _tcons!=null )
      for( String name : _tcons.keySet() ) {
        TCon tcon = _tcons.get(name);
        tcon.setype(repo);      // Pre-compute, so later types can find it
        env_add(name,tcon.tvar());
      }
    // This name->tvar mapping now needs to appear in the future environment
    // searches during setype analysis.  This is a class-wide type constant
    // name, and can be found by e.g. PropCon with the same name.  Search
    // path might look like:
    //    class->name2kid->(MMethod)name2kid->(Method)->args[]->Parameter
    //      ->_con-> [any type path] -> PropCon
    // Alternative: PropCon -> Class -> TCons -> this clz type.

    // This Class may extend another one.
    // Look for extends, implements, etc contributions
    if( _contribs != null ) {
      for( Contrib c : _contribs ) {
        switch( c._comp ) {
        case Extends, Implements, Delegates -> {
          assert c._comp != Composition.Extends || _contribs[0] == c; // Can optimize if extends are always in slot 0
          c.link( repo );
          TermTCon tc;
          if( c._tContrib instanceof TermTCon tc2 ) tc = tc2;
          else if( c._tContrib instanceof ImmutTCon itc )
            tc = (TermTCon)itc.icon();
          else throw XEC.TODO();
          env_extend( tc.clz() );
        }

        // This is a "mixin", marker interface.  It becomes part of the parent-
        // chain of the following "linked list" of classes.  The linked list
        // is formed from a left-spline UnionTCon.
        case Into -> {
          TCon mixed = c._tContrib;
          while( mixed instanceof UnionTCon utc ) {
            TermTCon tc = (TermTCon)utc.con2();
            tc.setype(repo);
            ClassPart clz = tc.clz();
            clz.env_extend( this );
            mixed = utc.con1();
          }
          TermTCon tc = (TermTCon)mixed;
          tc.setype(repo);
          ClassPart clz = tc.clz();
          clz.env_extend( this );
          throw XEC.TODO();
        }
        }
      }
    }
    set_env_lock();           // No more env extends

    // Now add typedefs to the type environment and value fields to the self
    // struct fields.
    for( String s : _name2kid.keySet() ) {
      if( _name2kid.get(s) instanceof TDefPart ) {
        env_add(s,new TVLeaf());// Add typdef to local type namespace
      } else if( _name2kid.get(s) instanceof PropPart ) {
        // Property
        
        // A repeat in the tcons?  This is another way to find generic type name.
        // The property here has more info
        if( _tcons!=null && _tcons.get(s)!=null ) {
        } else {
          // This is an XTC "property" - a field with built-in getters & setters.
          // These built-in fields have mangled names.
          stvar().add_fld(s+".get",new TVLeaf());
          stvar().add_fld(s+".set",new TVLeaf());
        }
      } else {
        assert env_get(s)==null; // No name collision
        stvar().add_fld(s,new TVLeaf());
      }
    }

    // All other contributions
    if( _contribs != null ) {
      for( Contrib c : _contribs ) {
        switch( c._comp ) {
        case Extends:
        case Implements:
        case Delegates:
          break;                // Already did
        default:
          // Handle other contributions
          throw XEC.TODO();
        }
      }
    }
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
