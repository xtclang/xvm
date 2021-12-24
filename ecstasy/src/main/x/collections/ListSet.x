/**
 * ListSet is an implementation of a Set on top of an Array to maintain the order of
 * insertion.
 */
class ListSet<Element>
         extends MapSet<Element>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ListSet that optionally contains an initial set of values.
     *
     * @param values        (optional) initial values to store in the ListSet
     * @param initCapacity  (optional) initial capacity of the ListSet
     */
    construct(Iterable<Element>? values = Null, Int initCapacity = 0)
        {
        ListMap<Element, Nullable> map = new ListMap(values?.size : initCapacity);
        for (Element value : values?)
            {
            map.put(value, Null);
            }
        construct MapSet(map);
        }

    /**
     * [Duplicable] constructor.
     *
     * TODO GG compilation succeeded without this constructor existing; that _seems_ wrong (since
     *         there's code that says "new ListSet(..)") ... or is it allowed because it finds the
     *         above-constructor and assumes that's good enough to support the Duplicable contract?
     *
     * @param that  the [Duplicable] `MapSet` object to duplicate from
     */
    construct(ListSet<Element> that)
        {
        construct MapSet(that);
        }
    }
