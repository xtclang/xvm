package org.xvm.xrun;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.TCon;
import org.xvm.xec.XClz;

import static org.xvm.xec.XClz.Mutability.*;

import java.util.Objects;
import java.util.HashMap;

public abstract class XMap<K,V> extends XClz implements Cloneable {
  private final HashMap<K,V> _map;

  public XMap() { _map = new HashMap<>(); }

  public void put( K key, V val ) { _map.put(key,val); }

  @Override 
  public final String toString() { return _map.toString(); }

  // Java default equals.
  // TODO: This needs to use XTC equals
  @Override public boolean equals( Object o ) {
    if( o==this ) return true;
    if( !(o instanceof XMap that) ) return false;
    return _map.equals(that._map);
  }
  
  // Return a tuple class for this set of types.  The class is cached, and can
  // be used many times.
  public static String make_class( HashMap<String,String> cache, TCon[] parms ) {
    XType xkey = XType.xtype(parms[0],true);
    XType xval = XType.xtype(parms[1],true);
    SB sb = new SB().p("XMap$");
    xkey.str(sb).p("$");
    xval.str(sb);
    String tclz = sb.toString();
    sb.clear();

    // Lookup cached version
    if( !cache.containsKey(tclz) ) {
      // XMap N class
      sb.p("class ").p(tclz).p(" extends XMap<");
      xkey.str(sb).p(",");
      xval.str(sb).p("> {").nl().ii();
      sb.di().ip("}").nl();
      cache.put(tclz,sb.toString());
    }

    return tclz;
  }
}
