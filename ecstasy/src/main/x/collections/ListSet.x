/**
 * ListSet is an implementation of a Set on top of an Array to maintain the order of
 * insertion.
 */
class ListSet<Element>
         extends MapSet<Element>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ListSet that optionally contains an initial set of values. The [NaturalHasher]
     * implementation will be used.
     *
     * @param values (optional) initial values to store in the ListSet
     */
    construct()
        {
        assert(Element.is(Type<Hashable>));
        construct ListSet(new NaturalHasher<Element>());
        }

    construct(Iterable<Element> values)
        {
        assert(Element.is(Type<Hashable>));
        construct ListSet(new NaturalHasher<Element>(), values);
        }

    /**
     * Construct a ListSet that relies on an external hasher, and optionally contains an initial set
     * of values.
     *
     * @param hasher        the [Hasher] to use for the values stored in the set
     * @param values        (optional) initial values to store in the ListSet
     * @param initCapacity  (optional) initial capacity of the ListSet
     */
    construct(Hasher<Element> hasher, Iterable<Element>? values = Null, Int initCapacity = 0)
        {
        HashMap<Element, Nullable> map = new HashMap(hasher, values?.size : initCapacity);
        for (Element value : values?)
            {
            map.put(value, Null);
            }
        construct MapSet(map);
        }

    /**
     * (Internal) Construct a ListSet that delegates storage to the specified map.
     *
     * @param map   the map to delegate storage to
     */
    private construct(Map<Element, Nullable> map)
        {
        construct MapSet(map);
        }

    @Override
    protected ListSet setFor(Map<Element, Nullable> map)
        {
        return new ListSet(map);
        }
    }
