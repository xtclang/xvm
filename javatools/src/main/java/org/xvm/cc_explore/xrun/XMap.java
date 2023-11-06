package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.xclz.XClzBuilder;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.xclz.XClz;
import org.xvm.cc_explore.xclz.XType;

import static org.xvm.cc_explore.xclz.XClz.Mutability.*;

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
    String zkey = XType.jtype(parms[0],true);
    String zval = XType.jtype(parms[1],true);
    SB sb = new SB().p("XMap$").p(zkey).p("$").p(zval);
    String tclz = sb.toString();
    sb.clear();

    // Lookup cached version
    if( !cache.containsKey(tclz) ) {
      // XMap N class
      sb.p("class ").p(tclz).p(" extends XMap<").p(zkey).p(",").p(zval).p("> {").nl().ii();
      sb.di().ip("}").nl();
      cache.put(tclz,sb.toString());
    }

    return tclz;
  }
}