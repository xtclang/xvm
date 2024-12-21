package org.xvm.util.converter;

import java.util.Set;

/**
 * A delegating {@link Set} with value conversion.
 *
 * @author falcom
 */
public abstract class AbstractConverterSet<V, SV> extends AbstractConverterCollection<V, SV> implements Set<V>
    {
    /**
     * Construct a {@link AbstractConverterSet}.
     *
     * @param storage the backing store
     */
    protected AbstractConverterSet(Set<SV> storage)
        {
        super(storage);
        }
    }
