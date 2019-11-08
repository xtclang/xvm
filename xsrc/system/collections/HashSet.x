/**
 * An implementation of a Set that is backed by a HashMap.
 */
class HashSet<Element>
        extends MapSet<Element>
        // TODO variably mutable implementations to match HashMap
    {
    /**
     * Construct a HashSet that optionally contains an initial set of values. The [NaturalHasher]
     * implementation will be used.
     *
     * @param values (optional) initial values to store in the HashSet
     */
    construct(Iterable<Element>? values = Null)
        {
        assert(Element.is(Type<Hashable>));
        construct HashSet(new NaturalHasher<Element>(), values);
        }

    /**
     * Construct a HashSet that relies on an external hasher, and optionally contains an initial set
     * of values.
     *
     * @param hasher  the [Hasher] to use for the values stored in the set
     * @param values  (optional) initial values to store in the HashSet
     */
    construct(Hasher<Element> hasher, Iterable<Element>? values = Null)
        {
        HashMap<Element, Nullable> map = new HashMap(hasher, values?.size : 0);
        for (Element value : values?)
            {
            map.put(value, Null);
            }
        construct MapSet(map);
        }

    /**
     * (Internal) Construct a HashSet that delegates storage to the specified map.
     *
     * @param map   the map to delegate storage to
     */
    private construct(Map<Element, Nullable> map)
        {
        construct MapSet(map);
        }

    @Override
    protected HashSet setFor(Map<Element, Nullable> map)
        {
        return new HashSet(map);
        }
    }
