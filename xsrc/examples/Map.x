
// <KeyType> - either a Value or there is a known Hasher<KeyType>

interface Map<KeyType, ValueType>
    {
    @ro Int size;
    @ro Boolean empty;

    @ro Boolean containsKey(KeyType key);

    @ro ValueType? get(KeyType key);

    @ro Entry<KeyType, ValueType>? getEntry(KeyType key);

    void put(KeyType key, ValueType value);
    void remove(KeyType key);

    void clear();




    @ro Set<K> keys(); // @ro Set
    Set<K> keys();

    @ro void forEach(Boolean (Entry<KeyType, ValueType>) action);

    @ro Collection<ValueType> values();

    typedef EntryPredicate function Boolean (Entry<KeyType, ValueType>);

    @ro Iterator(Entry<KeyType, ValueType>) entries(EntryPredicate test);

    @ro Iterator(Entry<KeyType, ValueType>) entries(Predicate<Entry<KeyType, ValueType>> test);
    @ro Iterator(Entry<KeyType, ValueType>) entries(function Boolean (<Entry<KeyType, ValueType>>) test);
    }

interface Entry<KeyType, ValueType>
    {
    @ro KeyType key;

    ValueType value;
    }
