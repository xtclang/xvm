package org.xvm.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * A filtering {@link java.util.Iterator}.
 *
 * @author falcom
 */
public class FilterIterator<T> implements Iterator<T>
    {
    /**
     * The delegate iterator.
     */
    private final Iterator<T> delegate;

    /**
     * The filter.
     */
    private final Predicate<? super T> filter;

    /**
     * pre-filtered next value
     */
    private T next;

    /**
     * {@code true} if {@link #remove()} is allowed
     */
    private boolean removeReady;

    public FilterIterator(Iterator<T> delegate, Predicate<? super T> filter)
        {
        this.delegate = delegate;
        this.filter = filter;
        }

    @Override
    public boolean hasNext()
        {
        removeReady = false;
        while (next == null && delegate.hasNext())
            {
            T item = delegate.next();
            if (filter.test(item))
                {
                next = item;
                }
            }

        return next != null;
        }

    @Override
    public T next()
        {
        if (hasNext())
            {
            T result = next;
            next = null;
            removeReady = true;
            return result;
            }
        throw new NoSuchElementException();
        }

    @Override
    public void remove()
        {
        if (removeReady)
            {
            removeReady = false;
            delegate.remove();
            }
        else
            {
            throw new IllegalStateException();
            }
        }
    }
