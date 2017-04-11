class HashMap<KeyType extends immutable Hashable, ValueType>
        extends ExtHashMap<KeyType, ValueType>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct HashMap(Int initCapacity = 0)
        {
        construct ExtHashMap(new NaturalHasher<KeyType>(), initCapacity);
        }
    }
