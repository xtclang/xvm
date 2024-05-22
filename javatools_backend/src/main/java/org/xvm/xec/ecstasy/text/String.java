package org.xvm.xec.ecstasy.text;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Iterable;
import org.xvm.xec.ecstasy.Iterator;
import org.xvm.xec.ecstasy.Ordered;
import org.xvm.xrun.Never;
import org.xvm.xrun.XRuntime;

public class String extends Const implements Iterable<Char> {
  public static final String GOLD = new String((Never)null);
  public String(Never n) { _i=null; }
  public final java.lang.String _i;
  public String(java.lang.String s) { _i = s; }

  public static String make(java.lang.String s) { return new String(s); }
  public static String construct(java.lang.String s) { return new String(s); } // TODO: Intern

  public int length() { return _i.length(); }
  @Override public int size$get() { return length(); }
  public char charAt(int x) { return _i.charAt(x); }
  @Override public java.lang.String toString() { return _i; }

  public static java.lang.String quoted( java.lang.String s ) {
    throw XEC.TODO();
  }


  public static <E extends String> boolean equals$String( XTC gold, E ord0, E ord1 ) { return ord0._i.equals(ord1._i); }
  public static <E extends String> boolean equals$String( XTC gold, E ord0, java.lang.String s1 ) { return ord0._i.equals(s1); }
  public static boolean equals$String( XTC gold, java.lang.String s0, java.lang.String s1 ) { return s0.equals(s1); }
  public static boolean equals$String( XTC gold, XTC c0, XTC c1 ) { return equals$String(gold,(String)c0,(String)c1); }

  @Override public boolean equals( XTC s0, XTC s1 ) {
    return equals(((String)s0)._i,((String)s1)._i);
  }

  @Override public Ordered compare( XTC s0, XTC s1 ) {
    return compare(((String)s0)._i,((String)s1)._i);
  }

  public Ordered compare( java.lang.String s0, java.lang.String s1 ) {
    int sig = s0.compareTo(s1);
    if( sig==0 ) return Ordered.Equal;
    return sig<0 ? Ordered.Lesser : Ordered.Greater;
  }

  public boolean equals( java.lang.String s0, java.lang.String s1 ) { return s0.equals(s1); }
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    return o instanceof String s && _i.equals(s._i);
  }

  public long hashCode( java.lang.String s0 ) { return s0.hashCode(); }
  @Override public long hashCode( XTC s0 ) { return hashCode(((String)s0)._i); }
  public static long hashCode$String( XTC gold, java.lang.String s0 ) { return s0.hashCode(); }

  @Override public Iterator<Char> iterator()                    { return new IterStr(_i); }
  static    public Iterator<Char> iterator(java.lang.String s0) { return new IterStr(s0); }
  private static class IterStr extends Iterator<Char> {
    final java.lang.String _s;
    int _i;
    IterStr(java.lang.String s) { _s = s; }
    @Override public Char next() { throw XEC.TODO(); }
    @Override public boolean hasNext() { return _i < _s.length(); }
    @Override public char next2() {
      boolean has = XRuntime.$COND = _i < _s.length();
      return has ? _s.charAt(_i++) : (char)0;
    }
    @Override public long next8() { throw XEC.TODO(); }
    @Override public java.lang.String nextStr() { throw XEC.TODO(); }
  }


  // --- Freezable
  @Override public String freeze(boolean inPlace) { return this; }

}
