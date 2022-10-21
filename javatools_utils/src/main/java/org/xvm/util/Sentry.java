package org.xvm.util;


import java.util.function.Consumer;


/**
 * A {@link java.lang.ref.Reference Reference} like object which is also {@link AutoCloseable}.
 *
 * @param <T> the value type
 *
 * @author mf
 */
@FunctionalInterface
public interface Sentry<T>
        extends AutoCloseable
    {
    @Override
    void close();

    /**
     * Return the value.
     * <p>
     * Calling this method or using a previously returned value after the sentry has been
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
     * @param <T>    the value type
     * @param value  the value to hold
     *
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
     * @param <T>     the value type
     * @param value   the value to hold
     * @param closer  the function to run upon {@link #close}.
     *
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