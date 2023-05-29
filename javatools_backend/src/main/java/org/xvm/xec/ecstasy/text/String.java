package org.xvm.xec.ecstasy.text;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xrun.Never;
import java.util.Objects;

public class String extends Const {
  public static final String GOLD = new String((Never)null);
  public String(Never n) { _s=null; }
  public final java.lang.String _s;
  public String(java.lang.String s) { _s = s; }
  public static String make(java.lang.String s) { return new String(s); } // TODO: Intern
  public int length() { return _s.length(); }
  public char charAt(int x) { return _s.charAt(x); }
  @Override public java.lang.String toString() { return _s; }
  
  
  public static <E extends String> boolean equals$String( XTC gold, E ord0, E ord1 ) { return ord0._s.equals(ord1._s); }
  public static boolean equals$String( XTC gold, java.lang.String s0, java.lang.String s1 ) { return s0.equals(s1); }
  
  @Override public boolean equals( XTC s0, XTC s1 ) {
    return equals(((String)s0)._s,((String)s1)._s);
  }
  
  public boolean equals( java.lang.String s0, java.lang.String s1 ) { return Objects.equals(s0,s1); }

  @Override public Ordered compare( XTC s0, XTC s1 ) {
    return compare(((String)s0)._s,((String)s1)._s);
  }
  
  public Ordered compare( java.lang.String s0, java.lang.String s1 ) {
    int sig = s0.compareTo(s1);
    if( sig==0 ) return Ordered.Equal;
    return sig<0 ? Ordered.Lesser : Ordered.Greater;
  }
  
  public long hashCode( java.lang.String s0 ) { return s0.hashCode(); }
  @Override public long hashCode( XTC s0 ) { return hashCode(((String)s0)._s); }
  public static long hashCode$String( XTC gold, java.lang.String s0 ) { return s0.hashCode(); }
  
}
