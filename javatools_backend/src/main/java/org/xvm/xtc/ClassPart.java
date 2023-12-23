package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.util.SB;
import org.xvm.xtc.cons.*;

import static org.xvm.xtc.Part.Composition.*;

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
  public final HashMap<String,TCon> _tcons; // String->TCon mapping
  public final LitCon _path;                // File name compiling this file
  public final Part.Format _f; // Class, Interface, Mixin, Enum, Module, Package

  public ClassPart _super; // Super-class.  Note that "_par" field is the containing Package, not the superclass

  // A list of "extra" features about Classes: extends, implements, delegates
  public final Contrib[] _contribs;

  public SB _header, _body;     // Java source code
  public XClz _tclz;            // XType class
  public Class<XTC> _jclz;      // Matching java class
  
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
      }
  }

  
  // Hunt this clz for name, plus recursively any Implements/Extends/Delegates
  // contribs.  "Into" contribs are search 1 level deep only.  Order matters on
  // the contribs, as the most specific hit should come first - but there may
  // be more hits later with more general names.
  //
  // E.g.
  // Looking for "Entry" in SkipListMap.
  // SkipListMap implements OrderedMap implements "Entry" - as an @Override interface.
  // SkipListMap incorporates CopyableMap.ReplicableCopier which implements CopyableMap
  // which implements Map which implements "Entry".
  // Map.Entry is the parent interface; OrderedMap.Entry is the child interface, it
  // extends Map.Entry as well as other extends.

  @Override public Part child(String s) {  return search(s,true);  }

  Part search( String s, boolean into ) {
    Part p = _name2kid==null ? null : _name2kid.get(s);
    if( p!=null ) return p;

    // Search contributions, perhaps recursively.  Check them all; should be
    // exactly 1 hit but for an assert confirm there is only one.
    if( _contribs == null ) return null;
    for( Contrib c : _contribs ) {
      switch( c._comp ) {
      case Into: if( !into ) continue; break;
      case Incorporates: into=false; break;
      case Implements: case Extends: case Delegates: break;
      default: continue;
      }
      c._tContrib.link(null);
      p = ((ClzCon)c._tContrib).clz().search(s,into);
      if( p!=null ) return p;
    }
    return null;
  }

  // True if this is a subclass of the given super
  public boolean subclass( ClassPart sup ) {
    return this==sup || (_super!=null && _super.subclass(sup));
  }

  // Modules in XTC do not have to be named after their file path, like they do in Java.
  // Example: XTC Module ecstasy.xtclang.org is in a file ecstasy.x.
  // I am using the file name as a Java file name... which is also the Java package.
  // XTC classes can use their given name, and this will be prefixed with the module as needed.
  String name() { return _name; }
  
  // Module for this class
  public ModPart mod() {
    Part clz = this;
    while( !(clz instanceof ModPart mod) )
      clz = clz._par;
    return mod;
  }

}
