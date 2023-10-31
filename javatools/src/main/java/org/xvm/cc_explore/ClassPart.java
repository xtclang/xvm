package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.SB;
import static org.xvm.cc_explore.Part.Composition.*;

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
Can drop the env lookup I think.

 */
public class ClassPart extends Part {
  private final HashMap<String,TCon> _tcons; // String->TCon mapping
  public final LitCon _path;                 // File name compiling this file
  final Part.Format _f;         // Class, Interface, Mixin, Enum, Module, Package

  public ClassPart _super; // Super-class.  Note that "_par" field is the containing Package, not the superclass

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
      for( Contrib c : _contribs ) {
        c.link( repo );
        if( c._comp==Extends )
          _super = ((ClzCon)c._tContrib).clz();
        else if( c._comp==Incorporates || c._comp==Annotation ) {
          ClassPart mix = ((ClzCon)c._tContrib).clz();
          if( _mixes==null ) _mixes = new ClassPart[1];
          else _mixes = Arrays.copyOfRange(_mixes,0,_mixes.length+1);
          _mixes[_mixes.length-1] = mix;          
        }
      }
  }

  
  @Override public Part child(String s) {  return search(s,null);  }

  // Hunt this clz for name, plus recursively any Implements contribs.
  // Assert only 1 found
  Part search( String s, Part p ) {
    Part p0 = _name2kid==null ? null : _name2kid.get(s);
    if( p0!=null ) {
      assert p==null || p==p0;
      return p0;
    }
    if( _contribs != null )
      for( Contrib c : _contribs )
        if( c._comp==Implements || c._comp==Into || c._comp==Extends || c._comp==Delegates ) {
          c._tContrib.link(null);
          p = ((ClzCon)c._tContrib).clz().search(s,p);
        }
    return p;
  }
  
  // Mangle a generic type name
  private String generic( String s ) {
    return (_name+".generic."+s).intern();
  }

}
