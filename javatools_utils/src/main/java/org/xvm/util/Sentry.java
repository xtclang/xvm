package org.xvm.util;

import java.util.function.Consumer;

/**
 * A {@link java.lang.ref.Reference Reference} like object which is also {@link AutoCloseable}.
 *
 * @param <T> the value type
 *
 * @author mf
 */
public interface Sentry<T>
    extends AutoCloseable
    {
    @Override
    void close();

    /**
     * Return the value.
     * <p>
     * Calling this method or using a previously returned value after then sentry has been
     * {@link #close closed} is illegal and results in undefined behavior.
     *
     * @return the value
     */
    default T get()
        {
        return null;
        }

    /**
     * Return a new {@link Sentry} holding the specified value.
     *
     * @param value the value to hold
     * @param <T> the value type
     * @return the sentry
     */
    static <T> Sentry<T> of(T value)
        {
        return new Sentry<>()
            {
            @Override
            public void close()
                {
                }

            @Override
            public T get()
                {
                return value;
                }
            };
        }

    /**
     * Return a new {@link Sentry} holding the specified value.
     *
     * @param value the value to hold
     * @param closer the function to run upon {@link #close}.
     * @param <T> the value type
     * @return the sentry
     */
    static <T> Sentry<T> of(T value, Consumer<? super T> closer)
        {
        return new Sentry<>()
            {
            @Override
            public void close()
                {
                closer.accept(get());
                }

            @Override
            public T get()
                {
                return value;
                }
            };
        }
    }
