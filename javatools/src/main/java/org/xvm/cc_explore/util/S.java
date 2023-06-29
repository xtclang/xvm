package org.xvm.cc_explore.util;

// Force strings to be interned, so we can use ptr-equals as equals
public abstract class S {
  public static boolean eq(String s0, String s1) {
    if( s0==s1 ) return true;
    assert s0==s0.intern();
    assert s1==s1.intern();
    return false;
  }
}
