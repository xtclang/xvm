class HashMap<KeyType, ValueType>
        implements Map<KeyType extends immutable Hashable, ValueType>
    {
    @ro Int size;

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
    }
