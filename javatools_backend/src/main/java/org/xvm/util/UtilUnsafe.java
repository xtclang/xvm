package org.xvm.util;
  
import java.lang.reflect.Field;

import sun.misc.Unsafe;

/**
 * Simple class to obtain access to the {@link Unsafe} object.  {@link Unsafe}
 * is required to allow efficient CAS operations on arrays.  Note that the
 * versions in java.util.concurrent.atomic, such as {@link
 * java.util.concurrent.atomic.AtomicLongArray}, require extra memory ordering
 * guarantees which are generally not needed in these algorithms and are also
 * expensive on most processors.
 */
public class UtilUnsafe {
  private UtilUnsafe() { } // dummy private constructor
  static final Unsafe UNSAFE = getUnsafe();
  /** Fetch the Unsafe.  Use With Caution. */
  public static Unsafe getUnsafe() {
    // Not on bootclasspath
    if( UtilUnsafe.class.getClassLoader() == null )
      return Unsafe.getUnsafe();
    try {
      final Field fld = Unsafe.class.getDeclaredField("theUnsafe");
      fld.setAccessible(true);
      return (Unsafe) fld.get(UtilUnsafe.class);
    } catch (Exception e) {
      throw new RuntimeException("Could not obtain access to sun.misc.Unsafe", e);
    }
  }

  static final long fieldOffset( Class clz, String field ) {
    Field f = null;
    try { f = clz.getDeclaredField(field); }
    catch( java.lang.NoSuchFieldException e ) { throw new RuntimeException(e); }
    return UNSAFE.objectFieldOffset(f);
  }
}
