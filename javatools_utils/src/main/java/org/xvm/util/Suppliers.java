package org.xvm.util;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Guava-style utility methods for working with {@link Supplier} instances.
 * Provides memoization (caching) wrappers for suppliers.
 *
 * <p>Use these to convert lazy-initialized getters into final fields. This class
 * provides just the memoizing supplier - for a richer API with {@code isComputed()},
 * {@code orElse()}, and {@code map()} methods, see {@link Lazy}.
 *
 * <h3>Real-world example from ClassStructure.getCanonicalType():</h3>
 * <pre>{@code
 * // Before (mutable, not thread-safe):
 * private transient TypeConstant m_typeCanonical;
 *
 * public TypeConstant getCanonicalType() {
 *     TypeConstant typeCanonical = m_typeCanonical;
 *     if (typeCanonical == null) {
 *         ConstantPool pool = getConstantPool();
 *         IdentityConstant constClz = getIdentityConstant();
 *         // ... compute typeCanonical ...
 *         m_typeCanonical = typeCanonical;
 *     }
 *     return typeCanonical;
 * }
 *
 * // After (final, thread-safe):
 * private final Supplier<TypeConstant> canonicalType =
 *     Suppliers.memoize(this::computeCanonicalType);
 *
 * public TypeConstant getCanonicalType() {
 *     return canonicalType.get();
 * }
 *
 * private TypeConstant computeCanonicalType() {
 *     ConstantPool pool = getConstantPool();
 *     IdentityConstant constClz = getIdentityConstant();
 *     // ... compute and return typeCanonical ...
 * }
 * }</pre>
 *
 * @see Lazy for a richer API with additional methods
 */
public final class Suppliers {

    private Suppliers() {
        // Utility class
    }

    /**
     * Returns a supplier that caches the result of the delegate's {@link Supplier#get()}
     * method. The returned supplier is thread-safe.
     *
     * <p>The delegate supplier is invoked at most once. After that, the cached value is
     * returned for all subsequent calls.
     *
     * <p>If the delegate returns null, null is cached and returned.
     *
     * @param delegate the supplier whose result should be cached
     * @param <T> the type of the supplied value
     * @return a memoizing supplier
     */
    public static <T> Supplier<T> memoize(Supplier<T> delegate) {
        Objects.requireNonNull(delegate, "delegate");

        // If already memoized, don't double-wrap
        if (delegate instanceof MemoizingSupplier) {
            return delegate;
        }

        return new MemoizingSupplier<>(delegate);
    }

    /**
     * Returns a supplier that caches the result for a specified duration.
     * After the duration expires, the next call will recompute the value.
     *
     * <p>This is useful for values that should be refreshed periodically,
     * such as configuration or external resource state.
     *
     * @param delegate the supplier whose result should be cached
     * @param duration the length of time to cache
     * @param unit the time unit of the duration
     * @param <T> the type of the supplied value
     * @return a time-limited memoizing supplier
     */
    public static <T> Supplier<T> memoizeWithExpiration(Supplier<T> delegate, long duration, TimeUnit unit) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(unit, "unit");
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be positive: " + duration);
        }

        return new ExpiringMemoizingSupplier<>(delegate, unit.toNanos(duration));
    }

    /**
     * Returns a supplier that always returns the same value.
     *
     * @param value the value to return
     * @param <T> the type of the value
     * @return a supplier that always returns the given value
     */
    public static <T> Supplier<T> ofInstance(T value) {
        return () -> value;
    }

    /**
     * Synchronizes access to the given supplier, ensuring that only one thread
     * can call {@link Supplier#get()} at a time.
     *
     * <p>Note: This does NOT memoize the result. Use {@link #memoize} for caching.
     *
     * @param delegate the supplier to synchronize
     * @param <T> the type of the supplied value
     * @return a synchronized supplier
     */
    public static <T> Supplier<T> synchronizedSupplier(Supplier<T> delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return new SynchronizedSupplier<>(delegate);
    }

    // -------------------------------------------------------------------------
    // Implementations
    // -------------------------------------------------------------------------

    /**
     * Thread-safe memoizing supplier using double-checked locking.
     */
    private static final class MemoizingSupplier<T> implements Supplier<T> {
        private static final Object UNSET = new Object();

        private final AtomicReference<Object> value = new AtomicReference<>(UNSET);
        private volatile Supplier<T> delegate;

        MemoizingSupplier(Supplier<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get() {
            Object v = value.get();
            if (v == UNSET) {
                synchronized (this) {
                    v = value.get();
                    if (v == UNSET) {
                        v = delegate.get();
                        value.set(v);
                        delegate = null; // Allow GC of delegate
                    }
                }
            }
            return (T) v;
        }

        @Override
        public String toString() {
            Object v = value.get();
            return "Suppliers.memoize(" + (v == UNSET ? delegate : "<computed>") + ")";
        }
    }

    /**
     * Memoizing supplier with time-based expiration.
     */
    private static final class ExpiringMemoizingSupplier<T> implements Supplier<T> {
        private final Supplier<T> delegate;
        private final long durationNanos;
        private final AtomicReference<T> value = new AtomicReference<>();
        private final AtomicLong expirationNanos = new AtomicLong();

        ExpiringMemoizingSupplier(Supplier<T> delegate, long durationNanos) {
            this.delegate = delegate;
            this.durationNanos = durationNanos;
        }

        @Override
        public T get() {
            long now = System.nanoTime();
            long expiration = expirationNanos.get();

            if (expiration == 0 || now >= expiration) {
                synchronized (this) {
                    expiration = expirationNanos.get();
                    if (expiration == 0 || now >= expiration) {
                        T newValue = delegate.get();
                        value.set(newValue);
                        // Set expiration after value to avoid race where another thread
                        // sees new expiration but old value
                        expirationNanos.set(now + durationNanos);
                        return newValue;
                    }
                }
            }
            return value.get();
        }

        @Override
        public String toString() {
            return "Suppliers.memoizeWithExpiration(" + delegate + ", " + durationNanos + " ns)";
        }
    }

    /**
     * Simple synchronized wrapper around a supplier.
     */
    private static final class SynchronizedSupplier<T> implements Supplier<T> {
        private final Supplier<T> delegate;

        SynchronizedSupplier(Supplier<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized T get() {
            return delegate.get();
        }

        @Override
        public String toString() {
            return "Suppliers.synchronizedSupplier(" + delegate + ")";
        }
    }
}
