/**
 * A Map is a _dictionary_ data structure that stores _values_, each identified by a _key_.
 *
 * The Map is one of the most commonly used data structures, because it allows information to be
 * easily _related to_ other information. As with many data structures, it is expected that
 * implementations will support one or more of these four modes, whose general behaviors are
 * defined as:
 * * A *mutable* map is one that allows items to be added and removed, and whose values for
 *   particular keys can be replaced, and whose contents are generally not required to be immutable.
 * * A *fixed size* map is one that does not allow items to be added or removed, but whose values
 *   for particular keys can be replaced, and whose contents are generally not required to be
 *   immutable. Requesting a persistent map to add or remove contents will result in a new fixed
 *   size map as a result of the request. If an implementation provides support for more than one
 *   mode, including a *fixed size* mode, then it should implement the {@link FixedSizeAble}
 *   interface.
 * * A *persistent* map is one that does not allow items to be added or removed, whose values for
 *   for particular keys can not be replaced, but whose contents are generally not required to be
 *   immutable. Requesting a persistent map to add, remove, or modify its contents will result in
 *   a new persistent map as a result of the request. If an implementation provides support for more
 *   than one mode, including a *persistent* mode, then it should implement the {@link
 *   PersistentAble} interface.
 * * A *const* map is one that is immutable, whose size and contents are immutable, and which
 *   provides a new *const* map as the result of any mutating request. If an implementation provides
 *   support for more than one mode, including a *const* mode, then it should implement the {@link
 *   ConstAble} interface.
 */
interface Map<KeyType, ValueType>
        implements UniformIndexed<IndexType, ElementType>
        implements Const
    {
    // ----- read operations ----

    @ro Int size;

    @ro Boolean empty.get()
        {
        return size > 0;
        }

    Boolean contains(KeyType key)
        {
        return get(key);
        }

    conditional ValueType get(KeyType key);

    ValueType? getOrNull(KeyType key)
        {
        if (ValueType value : get(key))
            {
            return value;
            }
        return null;
        }

    ValueType getOrDefault(KeyType key, ValueType defaultVal)
        {
        if (ValueType value : get(key))
            {
            return value;
            }
        return defaultVal;
        }

    @ro Set<KeyType> keys;

    @ro Collection<ValueType> values;

    @ro Set<Entry<KeyType, ValueType>> entries;

    // ----- write operations ----------------------------------------------------------------------

    Map<KeyType, ValueType> put(KeyType key, ValueType value);

    // TODO

    // ----- UniformedIndex ------------------------------------------------------------------------

    /**
     * Obtain the value of the specified element.
     */
    @op ValueType getElement(KeyType index)
        {
        if (ValueType value : get(index))
            {
            return value;
            }
        throw new BoundsException();
        }

    /**
     * Modify the value in the specified element.
     */
    @op Void setElement(KeyType index, ValueType value)
        {
        put(index, value);
        }

    // ----- Const interface -----------------------------------------------------------------------

    /**
     *
     */
    @ro Int hash.get()
        {
        return entries.hash;
        }

    /**
     * Two maps are equal iff they are they contain the same keys, and the value associated with
     * each key in the first map is equal to the value associated with the same key in the second
     * map.
     */
    static Boolean equals(Type<Map> MapType, MapType map1, MapType map2)
        {
        if (map1.size != map2.size)
            {
            return false;
            }

        for (Map.Entry<MapType.KeyType, MapType.ValueType>> entry : map1)
            {
            if (!(MapType.ValueType value : map2.get(entry.key) && entry.value == value))
                {
                return false;
                }
            }

        return true;
        }

    static Ordered compare(Type<Map> MapType, MapType map1, MapType map2)
        {
        // TODO
        }
    }
