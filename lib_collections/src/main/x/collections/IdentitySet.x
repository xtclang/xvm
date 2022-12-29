import ecstasy.collections.MapSet;

/**
 * An implementation of a Set that is backed by an IdentityMap.
 */
class IdentitySet<Element>
        extends MapSet<Element>
        implements Replicable
    {
    /**
     * Construct the IdentitySet with an (optional) initial capacity.
     *
     * @param initCapacity  (optional) the number of expected element values
     */
    @Override
    construct(Int initCapacity = 0)
        {
        construct MapSet(new IdentityMap<Element, Nullable>(initCapacity));
        }

    /**
     * Construct a IdentitySet that optionally contains an initial set of values.
     *
     * @param values  initial values to store in the IdentitySet
     */
    construct(Iterable<Element> values)
        {
        if (values.is(IdentitySet<Element>))
            {
            construct IdentitySet(values);
            }
        else
            {
            IdentityMap<Element, Nullable> map = new IdentityMap(values.size);
            for (Element value : values)
                {
                map.put(value, Null);
                }
            construct MapSet(map);
            }
        }

    @Override
    construct(IdentitySet that)
        {
        super(that);
        }
    }