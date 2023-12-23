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

  public static String java_class_name( String xname ) {
     return xname.replace(".","_");
   }

}
