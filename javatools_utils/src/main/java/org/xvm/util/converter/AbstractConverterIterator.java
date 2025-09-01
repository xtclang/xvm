package org.xvm.util.converter;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * A delegating {@link Iterator} which performs conversions.
 */
public abstract class AbstractConverterIterator<V, SV> implements Iterator<V> {
    /**
     * The delegate {@link Iterator}.
     */
    private final Iterator<SV> delegate;

    /**
     * Construct an {@link AbstractConverterIterator}.
     *
     * @param delegate the delegate iterator
     */
    protected AbstractConverterIterator(Iterator<SV> delegate) {
        this.delegate = delegate;
    }

    /**
     * @return the delegate iterator.
     */
    protected Iterator<SV> delegate() {
        return delegate;
    }

    /**
     * Convert from the storage value to the public value.
     *
     * @param value the storage value
     * @return the public value
     */
    abstract protected V valueUp(SV value);

    @Override
    public boolean hasNext() {
        return delegate().hasNext();
    }

    @Override
    public V next() {
        return valueUp(delegate().next());
    }

    @Override
    public void remove() {
        delegate().remove();
    }

    @Override
    public void forEachRemaining(Consumer<? super V> action) {
        delegate().forEachRemaining(sv -> action.accept(valueUp(sv)));
    }
}
