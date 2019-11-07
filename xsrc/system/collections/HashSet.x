/**
 * An implementation of a Set that is backed by a HashMap.
 */
class HashSet<Element>
        extends MapSet<Element>
        // TODO variably mutable implementations to match HashMap
    {
    construct(Collection<Element>? values = Null)
        {
        HashMap<Element, Nullable> map = new HashMap(values?.size : 0);
        for (Element value : values?)
            {
            map.put(value, Null);
            }
        construct MapSet(map);
        }

    construct(Hasher<Element> hasher, Collection<Element>? values = Null)
        {
        HashMap<Element, Nullable> map = new HashMap(hasher, values?.size : 0);
        for (Element value : values?)
            {
            map.put(value, Null);
            }
        construct MapSet(map);
        }

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
