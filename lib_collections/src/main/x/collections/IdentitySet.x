import ecstasy.collections.MapSet;

import ecstasy.maps.CopyableMap;

/**
 * An implementation of a [Set] that is backed by an [IdentityMap].
 */
class IdentitySet<Element>
        extends MapSet<Element>
        implements Replicable {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the `IdentitySet` with an optionally-specified capacity.
     *
     * This is the [Replicable] constructor.
     *
     * @param initCapacity  (optional) the number of expected `Element` values
     */
    @Override
    construct(Int initCapacity = 0) {
        construct MapSet(new IdentityMap<Element, Nullable>(initCapacity));
    }

    /**
     * Construct a `IdentitySet` that contains an initial set of values.
     *
     * @param values  initial values to store in the `IdentitySet`
     */
    construct(Iterable<Element> values) {
        if (values.is(IdentitySet<Element>)) {
            construct IdentitySet(values);
        } else {
            IdentityMap<Element, Nullable> map = new IdentityMap(values.size);
            for (Element value : values) {
                map.put(value, Null);
            }
            construct MapSet(map);
        }
    }

    /**
     * Construct the `IdentitySet` as a duplicate of another `IdentitySet`.
     *
     * This is the [Duplicable] constructor.
     *
     * @param that  the `IdentitySet` object to duplicate from
     */
    @Override
    construct(IdentitySet that) {
        super(that);
    }

    /**
     * Construct a `IdentitySet` that uses the specified [IdentityMap] for storage.
     *
     * @param map  the [IdentityMap] to use for storage
     */
    protected construct(IdentityMap<Element, Nullable> map) {
        construct MapSet(map);
    }

    // ----- internal ------------------------------------------------------------------------------

    @Override
    protected IdentitySet ensureMapSet(CopyableMap<Element, Nullable> map) {
        return &map == &contents
                ? this
                : new IdentitySet(map.as(IdentityMap<Element, Nullable>));
    }
}