package org.xvm.util.converter;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;

/**
 * A delegating {@link Collection} with value conversion.
 *
 * @author falcom
 */
public abstract class AbstractConverterCollection<V, SV> implements Collection<V>
    {
    /**
     * The collection to storage values in.
     */
    private final Collection<SV> storage;

    /**
     * Construct a {@link AbstractConverterCollection}.
     *
     * @param storage the backing store
     */
    protected AbstractConverterCollection(Collection<SV> storage)
        {
        this.storage = storage;
        }

    /**
     * @return the {@link #storage} for read operations.
     */
    protected Collection<SV> read()
        {
        return storage;
        }

    /**
     * @return the {@link #storage} for write operations.
     */
    protected Collection<SV> write()
        {
        return storage;
        }

    /**
     * Convert from the storage value to the public value.
     *
     * @param value the storage value
     * @return the public value
     */
    abstract protected V valueUp(SV value);

    /**
     * Convert from the public value to the storage value.
     *
     * @param value the public value
     * @return the storage value
     */
    abstract protected SV valueDown(V value);

    /**
     * Perform a cast to the desired type.
     *
     * @param x   the object to cast
     * @param <X> the type to cast to
     * @return the supplied object
     */
    @SuppressWarnings("unchecked")
    protected <X> X unchecked(Object x)
        {
        return (X) x;
        }

    @Override
    public int size()
        {
        return read().size();
        }

    @Override
    public boolean isEmpty()
        {
        return read().isEmpty();
        }

    @Override
    public boolean contains(Object o)
        {
        return read().contains(valueDown(unchecked(o)));
        }

    @Override
    public Iterator<V> iterator()
        {
        return new AbstractConverterIterator<>(read().iterator())
            {
            @Override
            protected V valueUp(SV value)
                {
                return AbstractConverterCollection.this.valueUp(value);
                }
            };
        }

    @Override
    public Object[] toArray()
        {
        Object[] values = read().toArray();
        for (int i = 0; i < values.length; ++i)
            {
            values[i] = valueUp(unchecked(values[i]));
            }
        return values;
        }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a)
        {
        Object[] values = toArray(); // performs the type conversion
        if (values.length > a.length)
            {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), values.length);
            }

        for (int i = 0; i < values.length; ++i)
            {
            a[i] = (T) values[i]; // already converted
            }
        return a;
        }

    @Override
    public boolean add(V v)
        {
        return write().add(valueDown(v));
        }

    @Override
    public boolean remove(Object o)
        {
        return write().remove(valueDown(unchecked(o)));
        }

    @Override
    public boolean containsAll(Collection<?> c)
        {
        for (var value : c)
            {
            if (!contains(value))
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public boolean addAll(Collection<? extends V> c)
        {
        boolean result = false;
        for (var value : c)
            {
            result |= add(value);
            }
        return result;
        }

    @Override
    public boolean removeAll(Collection<?> c)
        {
        boolean result = false;
        for (var value : c)
            {
            result |= remove(value);
            }
        return result;
        }

    @Override
    @SuppressWarnings("unchecked")
    public boolean retainAll(Collection<?> c)
        {
        return write().retainAll(new AbstractConverterCollection<SV, V>((Collection<V>) c)
            {
            @Override
            protected SV valueUp(V value)
                {
                return AbstractConverterCollection.this.valueDown(value);
                }

            @Override
            protected V valueDown(SV value)
                {
                return AbstractConverterCollection.this.valueUp(value);
                }
            });
        }

    @Override
    public void clear()
        {
        write().clear();
        }
    }
