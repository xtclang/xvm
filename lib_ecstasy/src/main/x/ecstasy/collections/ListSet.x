/**
 * ListSet is an implementation of a Set on top of an Array to maintain the order of
 * insertion.
 */
class ListSet<Element>
        extends MapSet<Element>
        implements Replicable {
        // TODO CP should be freezable
    // ----- constructors --------------------------------------------------------------------------

    /**
     * [Replicable] constructor.
     *
     * @param initCapacity  (optional) initial capacity of the ListSet
     */
    @Override
    construct(Int initCapacity = 0) {
        construct MapSet(new ListMap(initCapacity));
    }

    /**
     * Construct a ListSet that contains an initial set of values.
     *
     * @param values  initial values to store in the ListSet
     */
    construct(Iterable<Element> values) {
        ListMap<Element, Nullable> map = new ListMap(values.size);
        for (Element value : values) {
            map.put(value, Null);
        }
        construct MapSet(map);
    }

    /**
     * [Duplicable] constructor.
     *
     * @param that  the [Duplicable] `MapSet` object to duplicate from
     */
    @Override
    construct(ListSet<Element> that) {
        super(that);
    }
}