/**
 * An implementation of a Set that is backed by a HasherMap.
 *
 * TODO variably mutable implementations to match HasherMap
 */
class HasherSet<Element>
        extends MapSet<Element>
    {
    /**
     * Construct the HasherSet with the specified hasher and (optional) initial capacity.
     *
     * @param hasher        the [Hasher] to use
     * @param initCapacity  (optional) the number of expected element values
     */
    construct(Hasher<Element> hasher, Int initCapacity = 0)
        {
        construct MapSet(new HasherMap<Element, Nullable>(hasher, initCapacity));
        }

    /**
     * Construct a HasherSet that optionally contains an initial set of values.
     *
     * @param hasher  the [Hasher] to use
     * @param values  initial values to store in the HasherSet
     */
    construct(Hasher<Element> hasher, Iterable<Element> values)
        {
        if (values.is(HasherSet<Element>) && values.hasher == hasher)
            {
            construct HasherSet(values);
            }
        else
            {
            HasherMap<Element, Nullable> map = new HasherMap(hasher, values.size);
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
     * @param that  another HasherSet to copy the contents from when constructing this HasherSet
     */
    construct(HasherSet<Element> that)
        {
        construct MapSet(that);
        }

    /**
     * The [Hasher] is used to hash and compare element values.
     */
    Hasher<Element> hasher.get()
        {
        return contents.as(HasherMap<Element, Nullable>).hasher;
        }
    }
