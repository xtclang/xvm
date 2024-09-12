import maps.CopyableMap;

/**
 * An implementation of a [Set] that is backed by a [HashMap].
 */
class HashSet<Element extends Hashable>
        extends MapSet<Element>
        implements Replicable {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the `HashSet` with an (optional) initial capacity.
     *
     * @param initCapacity  (optional) the number of expected element values
     */
    @Override
    construct(Int initCapacity = 0) {
        construct MapSet(new HashMap<Element, Nullable>(initCapacity));
    }

    /**
     * Construct a `HashSet` that contains the specified values.
     *
     * @param values  initial values to store in the `HashSet`
     */
    construct(Iterable<Element> values) {
        if (values.is(HashSet<Element>)) {
            construct HashSet(values);
        } else {
            HashMap<Element, Nullable> map = new HashMap(values.size);
            for (Element value : values) {
                map.put(value, Null);
            }
            construct MapSet(map);
        }
    }

    @Override
    construct(HashSet that) {
        super(that);
    }

    /**
     * Construct a `HashSet` that uses the specified [HashMap] for storage.
     *
     * @param map  the [HashMap] to use for storage
     */
    protected construct(HashMap<Element, Nullable> map) {
        construct MapSet(map);
    }

    // ----- internal ------------------------------------------------------------------------------

    @Override
    protected HashSet ensureMapSet(CopyableMap<Element, Nullable> map) {
        return &map == &contents
                ? this
                : new HashSet(map.as(HashMap<Element, Nullable>));
    }
}