package org.xvm.cc_explore.util;


import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import java.util.function.Function;
import java.util.function.Supplier;


/**
 * A {@link ThreadLocal} variant optimized for short-lived thread-locals. Essentially, if the
 * reference to the thread-local is not {@code static}, it will be advantageous to use a
 * {@link TransientThreadLocal} rather than a {@link ThreadLocal}.
 *
 * @param <T> the value type
 *
 * @author mf
 */
public class TLS<T> extends ThreadLocal<T>  {

}
