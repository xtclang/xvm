package org.xvm.util;


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
public class TransientThreadLocal<T>
        extends ThreadLocal<T>
    {
    /**
     * Creates a thread local variable. The initial value of the variable is determined by invoking
     * the {@code get} method on the {@code Supplier}.
     *
     * @param <S>       the type of the thread local's value
     * @param supplier  the supplier to be used to determine the initial value
     *
     * @return a new thread local variable
     */
    public static <S> TransientThreadLocal<S> withInitial(Supplier<? extends S> supplier)
        {
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
        var map   = TRANSIENT_MAP.get();
        T   value = (T) map.get(this);

        if (value == null)
            {
            map.put(this, value = initialValue());
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
     * @param fn  the function to apply
     *
     * @return the new value
     */
    public T compute(Function<? super T, ? extends T> fn)
        {
        var map      = TRANSIENT_MAP.get();
        T   valueOld = (T) map.get(this);
        T   valueNew = fn.apply(valueOld == null ? initialValue() : valueOld);

        if (valueNew == null)
            {
            map.remove(this);
            }
        else
            {
            map.put(this, valueNew);
            }

        return valueNew;
        }

    /**
     * Apply the specified function if the current value is non-existent or {@code null} and update
     * to the value returned from the function, removing if {@code null}.
     *
     * @param fn  the function to apply
     *
     * @return the new value
     */
    public T computeIfAbsent(Supplier<? extends T> fn)
        {
        var map      = TRANSIENT_MAP.get();
        T   valueOld = (T) map.get(this);

        if (valueOld == null)
            {
            T valueNew = fn.get();

            if (valueNew != null)
                {
                map.put(this, valueNew);
                }
            return valueNew;
            }
        return valueOld;
        }

    /**
     * Update the value to the supplied value, and return an {@link AutoCloseable}, which restores
     * the former value when {@link AutoCloseable#close closed}.
     *
     * @param value  the new value
     *
     * @return a {@link AutoCloseable} which when closed will restore the prior value
     */
    public Sentry<T> push(T value)
        {
        var map          = TRANSIENT_MAP.get();
        T   valuePrev    = (T) map.put(this, value);
        T   valueRestore = valuePrev == null ? initialValue() : valuePrev;

        return valueRestore == null
                ? m_sentryRemove
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
                        map.put(TransientThreadLocal.this, valueRestore);
                        }
                    };
        }

    /**
     * The per-thread map of values keyed by the {@link TransientThreadLocal}.
     */
    private static final ThreadLocal<Map<TransientThreadLocal<?>, Object>> TRANSIENT_MAP
        = ThreadLocal.withInitial(IdentityHashMap::new);

    /**
     * A reusable function for invoking this::remove.
     */
    private final Sentry<T> m_sentryRemove = this::remove;
    }