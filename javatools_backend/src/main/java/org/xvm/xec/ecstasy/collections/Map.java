package org.xvm.xec.ecstasy.collections;

import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xec.ecstasy.numbers.Int64;

import java.util.HashMap;

public abstract class Map<K extends XTC, V extends XTC> extends XTC implements Stringable {
  private final HashMap<K,V> _map;

  public Map() { _map = new HashMap<>(); }

  public static <K extends XTC, V extends XTC> Map<K,V> make( Map<K,V> map ) { return map; }

  public void put( K key, V val ) { _map.put(key,val); }
  public void put( long key, String val ) { _map.put((K)Int64.make(key),(V)org.xvm.xec.ecstasy.text.String.make(val)); }

  // Java default equals.
  // TODO: This needs to use XTC equals
  @Override public boolean equals( Object o ) {
    if( o==this ) return true;
    return o instanceof Map that && _map.equals(that._map);
  }


  //// Return a tuple class for this set of types.  The class is cached, and can
  //// be used many times.
  //public static String make_class( HashMap<String,String> cache, TCon[] parms ) {
  //  XType xkey = XType.xtype(parms[0],true);
  //  XType xval = XType.xtype(parms[1],true);
  //  SB sb = new SB().p("XMap$");
  //  xkey.str(sb).p("$");
  //  xval.str(sb);
  //  String tclz = sb.toString();
  //  sb.clear();
  //
  //  // Lookup cached version
  //  if( !cache.containsKey(tclz) ) {
  //    // XMap N class
  //    sb.p("class ").p(tclz).p(" extends XMap<");
  //    xkey.str(sb).p(",");
  //    xval.str(sb).p("> {").nl().ii();
  //    sb.di().ip("}").nl();
  //    cache.put(tclz,sb.toString());
  //  }
  //
  //  return tclz;
  //}
}
