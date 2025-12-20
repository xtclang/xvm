package org.xvm.util;


import java.util.function.Supplier;


/**
 * A thread-safe memoizing supplier that computes its value lazily on first access.
 * <p>
 * This is equivalent to Guava's {@code Suppliers.memoize()} but without the Guava dependency.
 * The value is computed exactly once, even under concurrent access.
 * <p>
 * <b>Usage - Converting lazy null-check patterns to final fields:</b>
 * <pre>{@code
 * // BEFORE: Mutable field with lazy initialization
 * private TypeInfo m_typeInfo;
 *
 * public TypeInfo getTypeInfo() {
 *     if (m_typeInfo == null) {
 *         m_typeInfo = computeTypeInfo();
 *     }
 *     return m_typeInfo;
 * }
 *
 * // AFTER: Final field with Lazy
 * private final Lazy<TypeInfo> typeInfo = Lazy.of(this::computeTypeInfo);
 *
 * public TypeInfo getTypeInfo() {
 *     return typeInfo.get();
 * }
 * }</pre>
 * <p>
 * <b>Benefits:</b>
 * <ul>
 *   <li>Field can be {@code final} - enables immutability</li>
 *   <li>Thread-safe without explicit synchronization at call site</li>
 *   <li>Computation happens exactly once</li>
 *   <li>Clear intent - the field is lazy, not "maybe null"</li>
 * </ul>
 * <p>
 * <b>For non-thread-safe contexts</b>, use {@link #ofUnsafe(Supplier)} which avoids
 * synchronization overhead when thread safety is not required.
 *
 * @param <T> the type of value supplied
 */
public abstract class Lazy<T>
        implements Supplier<T> {

    /**
     * Create a thread-safe lazy supplier.
     * <p>
     * The delegate supplier will be called at most once, even under concurrent access.
     * After the first successful call, the computed value is cached and the delegate
     * is released (allowing it to be garbage collected).
     *
     * @param delegate the supplier to compute the value
     * @param <T>      the type of value
     * @return a memoizing supplier
     */
    public static <T> Lazy<T> of(Supplier<T> delegate) {
        return new ThreadSafeLazy<>(delegate);
    }

    /**
     * Create a non-thread-safe lazy supplier.
     * <p>
     * Use this when you know the supplier will only be accessed from a single thread,
     * or when external synchronization is already in place. This avoids the overhead
     * of synchronization.
     *
     * @param delegate the supplier to compute the value
     * @param <T>      the type of value
     * @return a memoizing supplier (not thread-safe)
     */
    public static <T> Lazy<T> ofUnsafe(Supplier<T> delegate) {
        return new UnsafeLazy<>(delegate);
    }

    /**
     * Create a lazy supplier that is already initialized with a value.
     * <p>
     * Useful for testing or when a value is known at construction time but you
     * want to maintain API compatibility with lazy fields.
     *
     * @param value the pre-computed value
     * @param <T>   the type of value
     * @return a supplier that always returns the given value
     */
    public static <T> Lazy<T> ofValue(T value) {
        return new Initialized<>(value);
    }

    /**
     * @return true if the value has been computed, false if {@link #get()} would
     *         trigger computation
     */
    public abstract boolean isInitialized();

    // ---- Thread-safe implementation --------------------------------------------------------

    private static final class ThreadSafeLazy<T> extends Lazy<T> {

        private volatile Supplier<T> delegate;
        private T value;

        ThreadSafeLazy(Supplier<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T get() {
            // Double-checked locking - safe because delegate is volatile
            if (delegate != null) {
                synchronized (this) {
                    if (delegate != null) {
                        value = delegate.get();
                        delegate = null;  // Release for GC
                    }
                }
            }
            return value;
        }

        @Override
        public boolean isInitialized() {
            return delegate == null;
        }

        @Override
        public String toString() {
            return delegate == null ? "Lazy[" + value + "]" : "Lazy[not initialized]";
        }
    }

    private static final class UnsafeLazy<T> extends Lazy<T> {
        private Supplier<T> delegate;
        private T value;

        UnsafeLazy(Supplier<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T get() {
            if (delegate != null) {
                value = delegate.get();
                delegate = null;
            }
            return value;
        }

        @Override
        public boolean isInitialized() {
            return delegate == null;
        }

        @Override
        public String toString() {
            return delegate == null
                    ? "Lazy[" + value + "]"
                    : "Lazy[not initialized]";
        }
    }

    /**
     * Pre-initialized implementation
     * @param <T>
     */
    private static final class Initialized<T> extends Lazy<T> {

        private final T value;

        Initialized(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public String toString() {
            return "Lazy[" + value + "]";
        }
    }
}
