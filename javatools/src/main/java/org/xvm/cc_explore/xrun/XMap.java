package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.xclz.XClzBuilder;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.xclz.XClz;

import static org.xvm.cc_explore.xclz.XClz.Mutability.*;

import java.util.Objects;
import java.util.HashMap;

public abstract class XMap<K,V> extends XClz implements Cloneable {
  private final HashMap<K,V> _map;

  public XMap() { _map = new HashMap<>(); }

  public void put( K key, V val ) { _map.put(key,val); }

  
  // Return a tuple class for this set of types.  The class is cached, and can
  // be used many times.
  public static String make_class( HashMap<String,String> cache, TCon[] parms ) {
    String zkey = XClzBuilder.jtype(parms[0],true);
    String zval = XClzBuilder.jtype(parms[1],true);
    SB sb = new SB().p("XMap$").p(zkey).p("$").p(zval);
    String tclz = sb.toString();
    sb.clear();

    // Lookup cached version
    if( !cache.containsKey(tclz) ) {
      /* Gotta build one.  Looks like:
      */
      // XMap N class
      sb.p("class ").p(tclz).p(" extends XMap<").p(zkey).p(",").p(zval).p("> {").nl().ii();
      //// N field declares
      //for( int i=0; i<N; i++ )
      //  sb.ip("public ").p(clzs[i]).p(" _f").p(i).p(";").nl();
      //// Constructor, taking N arguments
      //sb.ip(tclz).p("( ");
      //for( int i=0; i<N; i++ )
      //  sb.p(clzs[i]).p(" f").p(i).p(", ");
      //sb.unchar(2).p(") {").nl().ii().i();
      //// N arg to  field assigns
      //for( int i=0; i<N; i++ )
      //  sb.p("_f").p(i).p("=").p("f").p(i).p("; ");
      //sb.nl().di().ip("}").nl();
      //// Abstract accessors
      //for( int i=0; i<N; i++ )
      //  sb.ip("public Object f").p(i).p("() { return _f").p(i).p("; }").nl();
      //// Abstract setters
      //for( int i=0; i<N; i++ )
      //  sb.ip("public void f").p(i).p("(Object e) { _f").p(i).p("= (").p(XClzBuilder.box(clzs[i])).p(")e; }").nl();
      // Class end
      sb.di().ip("}").nl();
      cache.put(tclz,sb.toString());
    }

    return tclz;
  }
}
