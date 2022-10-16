package org.xvm.util;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link ThreadLocal} variant optimized for short-lived thread-locals. Esentially if the reference
 * to the thread-local is not {@code static} it will be advantageous to use a {@link TransientThreadLocal}
 * rather than a {@link ThreadLocal}.
 *
 * @param <T> the value type
 *
 * @author mf
 */
public class TransientThreadLocal<T>
    extends ThreadLocal<T>
    {
    /**
     * Creates a thread local variable. The initial value of the variable is
     * determined by invoking the {@code get} method on the {@code Supplier}.
     *
     * @param supplier the supplier to be used to determine the initial value
     * @param <S> the type of the thread local's value

     * @return a new thread local variable
     */
    public static <S> TransientThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
    Objects.requireNonNull(supplier, "null supplier");
    return new TransientThreadLocal<>()
        {
        @Override
        protected S initialValue()
            {
            return supplier.get();
            }
        };
    }

    @Override
    public T get()
        {
        var map = TRANSIENT_MAP.get();
        T value = (T) map.get(this);
        if (value == null)
            {
            value = initialValue();
            map.put(this, value);
            }

        return value;
        }

    @Override
    public void set(T value)
        {
        TRANSIENT_MAP.get().put(this, value);
        }

    @Override
    public void remove()
        {
        TRANSIENT_MAP.get().remove(this);
        }

    /**
     * Apply the specified function to the current value and update to the value returned from the
     * function, removing if {@code null}.
     *
     * @param f the function to apply
     * @return the new value
     */
    public T compute(Function<? super T, ? extends T> f) {
    var map = TRANSIENT_MAP.get();
    T old = (T) map.get(this);
    T updated = f.apply(old == null ? initialValue() : old);
    if (updated == null)
        {
        map.remove(this);
        }
    else
        {
        map.put(this, updated);
        }

    return updated;
    }

    /**
     * Apply the specified function if the current value is non-existent or {@code null} and update
     * to the value returned from the function, removing if {@code null}.
     *
     * @param f the function to apply
     * @return the new value
     */
    public T computeIfAbsent(Supplier<? extends T> f) {
    var map = TRANSIENT_MAP.get();
    T old = (T) map.get(this);
    if (old == null)
        {
        T updated = f.get();
        if (updated != null)
            {
            map.put(this, updated);
            }
        return updated;
        }
    return old;
    }

    /**
     * Updatae the value to the supplied value, and return an {@link AutoCloseable} which restores
     * the former value when {@link AutoCloseable#close closed}
     * @param value
     *
     * @return a {@link AutoCloseable} which when closed will restore the prior value
     */
    public Sentry<T> push(T value) {
    var map = TRANSIENT_MAP.get();
    T old = (T) map.put(this, value);
    T finOld = old == null ? initialValue() : old;

    return finOld == null
        ? remove
        : new Sentry<>()
        {
        @Override
        public T get()
            {
            return value;
            }

        @Override
        public void close()
            {
            map.put(TransientThreadLocal.this, finOld);
            }
        };
    }

    /**
     * The per-thread map of values key'd by the {@link TransientThreadLocal}.
     */
    private static final ThreadLocal<Map<TransientThreadLocal<?>, Object>> TRANSIENT_MAP
        = ThreadLocal.withInitial(IdentityHashMap::new);

    /**
     * A reusable function for invoking this::remove.
     */
    private final Sentry<T> remove = this::remove;
    }
