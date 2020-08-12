/**
 * An implementation of a Set that is backed by a HashMap.
 */
class HashSet<Element>
        extends MapSet<Element>
        // TODO variably mutable implementations to match HashMap
        // TODO make "freeze" virtual - to return HashSet, not MapSet
    {
    /**
     * Construct a HashSet that optionally contains an initial set of values. The [NaturalHasher]
     * implementation will be used.
     *
     * @param values (optional) initial values to store in the HashSet
     */
    construct(Iterable<Element+Hashable>? values = Null)
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
    }
