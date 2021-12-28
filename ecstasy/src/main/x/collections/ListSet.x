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
     * @param that  the [Duplicable] `MapSet` object to duplicate from
     */
    construct(ListSet<Element> that)
        {
        construct MapSet(that);
        }
    }
