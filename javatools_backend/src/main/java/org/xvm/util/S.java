package org.xvm.util;

// Force strings to be interned, so we can use ptr-equals as equals
public abstract class S {
  public static boolean eq(String s0, String s1) {
    if( s0==s1 ) return true;
    assert s0==null || s0==s0.intern();
    assert s1==null || s1==s1.intern();
    return false;
  }

  // Fast linear scan for a hit, returns index or -1.
  // Uses '==' not '.equals'
  public static <E> int find( E[] es, E e ) {
    if( es != null )
      for( int i=0; i<es.length; i++ ) if( es[i]==e ) return i;
    return -1;
  }
  public static int find( int[] es, int e ) {
    if( es != null )
      for( int i=0; i<es.length; i++ ) if( es[i]==e ) return i;
    return -1;
  }

  public static String java_class_name( String xname ) {
    int idx = findBad(xname);
    if( idx == -1 ) return xname;
    char[] cs = xname.toCharArray();
    for( int i=idx; i<cs.length; i++ )
      if( bad(cs[i]) ) cs[i] = '_';
    return new String(cs).intern();
  }
  private static int findBad( String xname ) {
    for( int i=0; i<xname.length(); i++ )
      if( bad(xname.charAt(i)) )
        return i;
    return -1;
  }
  private static boolean bad(char c) {
    return c=='.' || c==':' || c=='=';
  }

  public static <X> X[] swap( X[] ary, int i, int j ) {
    X tmp = ary[i];
    ary[i] = ary[j];
    ary[j] = tmp;
    return ary;
  }

  public static long rot(long x, int k) { return (x<<k) | (x>>>(64-k)); }
  public static int fold(long x) { return (int)((x>>32) ^ x); }
}
