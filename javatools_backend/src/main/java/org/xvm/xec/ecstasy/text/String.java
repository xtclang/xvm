package org.xvm.xec.ecstasy.text;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xrun.Never;
import java.util.Objects;

public class String extends Const {
  public static final String GOLD = new String((Never)null);
  public String(Never n) { _i=null; }
  public final java.lang.String _i;
  public String(java.lang.String s) { _i = s; }
  
  public static String construct(java.lang.String s) { return new String(s); } // TODO: Intern
  
  public int length() { return _i.length(); }
  public char charAt(int x) { return _i.charAt(x); }
  @Override public java.lang.String toString() { return _i; }

  public static java.lang.String quoted( java.lang.String s ) {
    throw XEC.TODO();
  }
  
  
  public static <E extends String> boolean equals$String( XTC gold, E ord0, E ord1 ) { return ord0._i.equals(ord1._i); }
  public static boolean equals$String( XTC gold, java.lang.String s0, java.lang.String s1 ) { return s0.equals(s1); }
  
  @Override public boolean equals( XTC s0, XTC s1 ) {
    return equals(((String)s0)._i,((String)s1)._i);
  }
  
  public boolean equals( java.lang.String s0, java.lang.String s1 ) { return s0.equals(s1); }

  @Override public Ordered compare( XTC s0, XTC s1 ) {
    return compare(((String)s0)._i,((String)s1)._i);
  }
  
  public Ordered compare( java.lang.String s0, java.lang.String s1 ) {
    int sig = s0.compareTo(s1);
    if( sig==0 ) return Ordered.Equal;
    return sig<0 ? Ordered.Lesser : Ordered.Greater;
  }
  
  public long hashCode( java.lang.String s0 ) { return s0.hashCode(); }
  @Override public long hashCode( XTC s0 ) { return hashCode(((String)s0)._i); }
  public static long hashCode$String( XTC gold, java.lang.String s0 ) { return s0.hashCode(); }
  
}
