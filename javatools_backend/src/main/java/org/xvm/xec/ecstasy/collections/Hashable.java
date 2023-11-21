package org.xvm.xec.ecstasy.collections;

public interface Hashable<E> extends Comparable<E> {
  // Empty interface from the Java side.

  // XTC interface demands a "static long hashCode(E)", but this does not help
  // the Java implementation, since there's no dispatch on a static.  The XTC
  // compiler ensures that the "static hashCode" exists and Java will call it
  // directly - without usinfg an interface.  Basically this is a marker
  // interface: this static call should exist in every Hashable namespace
  // but it cannot be dynamically called.
}
