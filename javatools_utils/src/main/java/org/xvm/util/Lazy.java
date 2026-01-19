package org.xvm.util;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Guava-style lazy value holders for thread-safe deferred initialization.
 * Use these instead of "if (field == null) { field = compute(); }" patterns.
 *
 * <p>This class provides a richer API than {@link Suppliers#memoize} with additional
 * methods like {@link #isComputed()}, {@link #orElse}, and {@link #map}. For a
 * simpler API that just returns {@code Supplier<T>}, use {@link Suppliers}.
 *
 * <h3>Real-world example from ClassStructure.collectAnnotations():</h3>
 * <pre>{@code
 * // Before (mutable, manual caching):
 * private transient Annotation[] m_aAnnoClass;
 *
 * public Annotation[] getClassAnnotations() {
 *     Annotation[] annos = m_aAnnoClass;
 *     if (annos == null) {
 *         List<Annotation> listAnnos = new ArrayList<>();
 *         for (Contribution contrib : getContributionsAsList()) {
 *             if (contrib.getComposition() == Composition.Annotation) {
 *                 listAnnos.add(contrib.getAnnotation());
 *             }
 *         }
 *         annos = listAnnos.toArray(Annotation.NO_ANNOTATIONS);
 *         m_aAnnoClass = annos;
 *     }
 *     return annos;
 * }
 *
 * // After (final, thread-safe, with additional API):
 * private final Lazy<Annotation[]> classAnnotations = Lazy.of(() -> {
 *     List<Annotation> listAnnos = new ArrayList<>();
 *     for (Contribution contrib : getContributionsAsList()) {
 *         if (contrib.getComposition() == Composition.Annotation) {
 *             listAnnos.add(contrib.getAnnotation());
 *         }
 *     }
 *     return listAnnos.toArray(Annotation.NO_ANNOTATIONS);
 * });
 *
 * public Annotation[] getClassAnnotations() {
 *     return classAnnotations.get();
 * }
 *
 * // Bonus: can check if computed without triggering computation
 * public boolean hasComputedAnnotations() {
 *     return classAnnotations.isComputed();
 * }
 * }</pre>
 *
 * @param <T> the type of the lazily computed value
 * @see Suppliers#memoize for a simpler memoizing supplier
 */
public abstract class Lazy<T> implements Supplier<T> {

    /**
     * Creates a thread-safe lazy value that computes on first access.
     * The supplier is called at most once, even under concurrent access.
     *
     * @param supplier the supplier to compute the value (called at most once)
     * @param <T> the type of the value
     * @return a lazy holder for the value
     */
    public static <T> Lazy<T> of(Supplier<T> supplier) {
        return new ThreadSafeLazy<>(supplier);
    }

    /**
     * Creates a non-thread-safe lazy value for single-threaded use.
     * Faster than {@link #of} but not safe for concurrent access.
     *
     * @param supplier the supplier to compute the value
     * @param <T> the type of the value
     * @return a lazy holder for the value
     */
    public static <T> Lazy<T> ofUnsynchronized(Supplier<T> supplier) {
        return new UnsynchronizedLazy<>(supplier);
    }

    /**
     * Creates a lazy value that is already computed (useful for testing or constants).
     *
     * @param value the pre-computed value
     * @param <T> the type of the value
     * @return a lazy holder containing the value
     */
    public static <T> Lazy<T> ofValue(T value) {
        return new Resolved<>(value);
    }

    /**
     * Creates a thread-safe lazy Optional that wraps nullable supplier results.
     * Use this instead of sentinel values (like 0L or -1) to represent "no value".
     *
     * <p>The supplier may return null, which will be wrapped as {@code Optional.empty()}.
     * Non-null values are wrapped as {@code Optional.of(value)}.
     *
     * <h3>Example - replacing sentinel value patterns:</h3>
     * <pre>{@code
     * // Before (using 0L as sentinel - bad because 0L could be valid):
     * private long timestamp;
     * public long getTimestamp() {
     *     if (timestamp == 0L) {
     *         File f = getFile();
     *         if (f != null && f.exists()) {
     *             timestamp = f.lastModified();
     *         }
     *     }
     *     return timestamp;
     * }
     *
     * // After (proper separation of "not computed" vs "no value"):
     * private final Lazy<Optional<Long>> timestamp = Lazy.ofNullable(() -> {
     *     File f = getFile();
     *     return f != null && f.exists() ? f.lastModified() : null;
     * });
     *
     * public Optional<Long> getTimestamp() {
     *     return timestamp.get();
     * }
     * }</pre>
     *
     * @param supplier the supplier that may return null (null becomes Optional.empty())
     * @param <T> the type of the value
     * @return a lazy holder for the Optional value
     */
    public static <T> Lazy<Optional<T>> ofNullable(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return of(() -> Optional.ofNullable(supplier.get()));
    }

    /**
     * Creates a thread-safe lazy Optional from a supplier that already returns Optional.
     * Use this when your computation naturally returns Optional.
     *
     * @param supplier the supplier returning an Optional
     * @param <T> the type of the value inside the Optional
     * @return a lazy holder for the Optional value
     */
    public static <T> Lazy<Optional<T>> ofOptional(Supplier<Optional<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return of(() -> {
            Optional<T> result = supplier.get();
            return result != null ? result : Optional.empty();
        });
    }

    /**
     * Gets the lazily computed value, computing it if necessary.
     *
     * @return the value
     */
    @Override
    public abstract T get();

    /**
     * Returns true if the value has been computed.
     *
     * @return true if already computed, false if still deferred
     */
    public abstract boolean isComputed();

    /**
     * If computed, returns the value; otherwise returns the default.
     *
     * @param defaultValue the value to return if not yet computed
     * @return the computed value or the default
     */
    public T orElse(T defaultValue) {
        return isComputed() ? get() : defaultValue;
    }

    /**
     * Maps the lazy value to another type.
     *
     * @param mapper the mapping function
     * @param <U> the result type
     * @return a new Lazy that maps this value
     */
    public <U> Lazy<U> map(java.util.function.Function<? super T, ? extends U> mapper) {
        return of(() -> mapper.apply(get()));
    }

    // -------------------------------------------------------------------------
    // Implementations
    // -------------------------------------------------------------------------

    /**
     * Thread-safe implementation using double-checked locking with AtomicReference.
     */
    private static final class ThreadSafeLazy<T> extends Lazy<T> {
        private static final Object UNSET = new Object();

        private final AtomicReference<Object> valueRef = new AtomicReference<>(UNSET);
        private volatile Supplier<T> supplier;

        ThreadSafeLazy(Supplier<T> supplier) {
            this.supplier = Objects.requireNonNull(supplier, "supplier");
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get() {
            Object value = valueRef.get();
            if (value == UNSET) {
                synchronized (this) {
                    value = valueRef.get();
                    if (value == UNSET) {
                        value = supplier.get();
                        valueRef.set(value);
                        supplier = null; // Allow GC of the supplier
                    }
                }
            }
            return (T) value;
        }

        @Override
        public boolean isComputed() {
            return valueRef.get() != UNSET;
        }
    }

    /**
     * Non-thread-safe implementation for single-threaded use.
     * Faster than ThreadSafeLazy but not safe for concurrent access.
     */
    private static final class UnsynchronizedLazy<T> extends Lazy<T> {
        private Supplier<T> supplier;
        private T value;
        private boolean computed;

        UnsynchronizedLazy(Supplier<T> supplier) {
            this.supplier = Objects.requireNonNull(supplier, "supplier");
        }

        @Override
        public T get() {
            if (!computed) {
                value = supplier.get();
                supplier = null; // Allow GC
                computed = true;
            }
            return value;
        }

        @Override
        public boolean isComputed() {
            return computed;
        }
    }

    /**
     * Pre-resolved value (no computation needed).
     */
    private static final class Resolved<T> extends Lazy<T> {
        private final T value;

        Resolved(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public boolean isComputed() {
            return true;
        }
    }
}