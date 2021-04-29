/**
 * An OrderedMap is an extension to the Map interface that exposes capabilities that are dependent
 * on an ordering of the keys (the entries) in the Map.
 */
interface OrderedMap<Key extends Orderable, Value>
        extends Map<Key, Value>
        extends Sliceable<Key>
    {
    @Override
    conditional Orderer ordered();

    /**
     * Obtain the first key in the OrderedMap.
     *
     * @return the True iff the Map is not empty
     * @return (conditional) the first key in the OrderedMap
     */
    conditional Key first();

    /**
     * Obtain the last key in the OrderedMap.
     *
     * @return the True iff the Map is not empty
     * @return (conditional) the last key in the OrderedMap
     */
    conditional Key last();

    /**
     * Obtain the key that comes immediately after the specified key in the Map.
     *
     * @param key  a key that may _or may not be_ already present in the Map
     *
     * @return the True iff the Map is not empty and has a key that comes after the specified key
     * @return (conditional) the next key
     */
    conditional Key next(Key key);

    /**
     * Obtain the key that comes immediately before the specified key in the Map.
     *
     * @param key  a key that may _or may not be_ already present in the Map
     *
     * @return the True iff the Map is not empty and has a key that comes before the specified key
     * @return (conditional) the previous key
     */
    conditional Key prev(Key key);

    /**
     * Obtain the key that comes at or immediately after the specified key in the Map.
     *
     * @param key  a key that may _or may not be_ already present in the Map
     *
     * @return the True iff the Map is not empty and has a key that comes at or after the specified
     *         key
     * @return (conditional) the key that was passed in, if it exists in the Map, otherwise the
     *         [next] key
     */
    conditional Key ceiling(Key key);

    /**
     * Obtain the key that comes at or immediately before the specified key in the Map.
     *
     * @param key  a key that may _or may not be_ already present in the Map
     *
     * @return the True iff the Map is not empty and has a key that comes at or before the specified
     *         key
     * @return (conditional) the key that was passed in, if it exists in the Map, otherwise the
     *         [prev] key
     */
    conditional Key floor(Key key);


    // ----- Entry ---------------------------------------------------------------------------------

    @Override
    interface Entry
            extends Orderable
        {
        static <CompileType extends Entry> Ordered compare(CompileType value1, CompileType value2)
            {
            assert val order  := value1.outer.ordered();
            assert val order2 := value2.outer.ordered(), order == order2;

            return order(value1.key, value2.key);
            }
        }


    // ----- equality ------------------------------------------------------------------------------

    /**
     * Two ordered maps are equal iff they are they contain the same keys in the same order, and the
     * value associated with each key in the first map is equal to the value associated with the
     * same key in the second map.
     */
    static <CompileType extends OrderedMap> Boolean equals(CompileType map1, CompileType map2)
        {
        // some simple optimizations: two empty maps are equal, and two maps of different sizes are
        // not equal
        if (Int size1 := map1.keys.knownSize(), Int size2 := map2.keys.knownSize())
            {
            if (size1 != size2)
                {
                return False;
                }
            else if (size1 == 0)
                {
                return True;
                }
            }
        else
            {
            switch (map1.empty, map2.empty)
                {
                case (False, False):
                    break;

                case (False, True ):
                case (True , False):
                    return False;

                case (True , True ):
                    return True;
                }
            }

        // compare all of the entries in the two ordered maps, in the order that they appear
        using (Iterator<CompileType.Entry> iter1 = map1.entries.iterator(),
               Iterator<CompileType.Entry> iter2 = map2.entries.iterator())
            {
            while (CompileType.Entry entry1 := iter1.next())
                {
                if (CompileType.Entry entry2 := iter2.next())
                    {
                    if (entry1 != entry2)
                        {
                        return False;
                        }
                    }
                else
                    {
                    return False;
                    }
                }

            return !iter2.next();
            }
        }
    }
