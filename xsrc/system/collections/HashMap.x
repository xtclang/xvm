class HashMap<KeyType extends immutable Hashable, ValueType>
        extends ExtHashMap<KeyType, ValueType>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct HashMap(Int initCapacity = 0)
        {
        construct ExtHashMap(new NaturalHasher<KeyType>(), initCapacity);
        }

// TODO

    conditional ValueType get(KeyType key)
        {
        if (Entry entry = getEntry(key)
            {
            return true, entry.value;
            }

        return false;
        }

    HashMap<KeyType, ValueType> put(KeyType key, ValueType value)
        {

        }

    // ...
    /**
     * Determine the hash value for the Map. A Map's hash value is computed in an accumulative
     * manner from its contents:
     * * The hash value begins as the size of the map itself;
     * * If the KeyType is Hashable, then the hash of each key is {@code xor}'d into the resulting
     *   hash value;
     * * If the ValueType is Hashable, then the hash of each value is {@code xor}'d into the
     *   resulting hash value.
     */
    @ro Int hash.get()
        {
        switch ((KeyType instanceof Hashable, ValueType instanceof Hashable))
            {
            case (true, true):
                return entries.hash;

            case (true, false):
                return keys.hash;

            case (false, true):
                return values.hash;

            default:
            case (false, false):
                return size;
            }
        }


    class Entry
        {
        /**
         * Determine the hash value for the Entry. An Entry's hash value is computed in an
         * accumulative manner from its contents:
         * * The hash value begins as 0.
         * * If the KeyType is Hashable, then the hash of the key is {@code xor}'d into the
         *   resulting hash value;
         * * If the ValueType is Hashable, then the hash of the value is {@code xor}'d into the
         *   resulting hash value.
         */
        @ro Int hash.get()
            {
            return (KeyType instanceof Hashable ? key.hash : 0) ^
                   (ValueType instanceof Hashable ? value.hash : 0);
            }

        }

    static Ordered compare(Type<Map> MapType, MapType map1, MapType map2)
        {
        // TODO
        }
    }
