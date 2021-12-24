/**
 * An implementation of a Set that is backed by a HashMap.
 *
 * TODO variably mutable implementations to match HashMap
 */
class HashSet<Element extends Hashable>
        extends MapSet<Element>
    {
    /**
     * Construct the HashSet with an (optional) initial capacity.
     *
     * @param initCapacity  (optional) the number of expected element values
     */
    construct(Int initCapacity = 0)
        {
        construct MapSet(new HashMap<Element, Nullable>(initCapacity));
        }

    /**
     * Construct a HashSet that optionally contains an initial set of values.
     *
     * @param values  initial values to store in the HashSet
     */
    construct(Iterable<Element> values)
        {
        if (values.is(HashSet<Element>))
            {
            construct HashSet(values);
            }
        else
            {
            HashMap<Element, Nullable> map = new HashMap(values.size);
            for (Element value : values)
                {
                map.put(value, Null);
                }
            construct MapSet(map);
            }
        }

    /**
     * [Duplicable] constructor.
     *
     * @param that  another HashSet to copy the contents from when constructing this HashSet
     */
    construct(HashSet<Element> that)
        {
        construct MapSet(that);
        }
    }
