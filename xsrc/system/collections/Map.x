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
        implements UniformIndexed<KeyType, ValueType>
    {
    // ----- Entry interface -----------------------------------------------------------------------

    /**
     * A Map Entry represents a single mapping from a particular key to its value.
     */
    interface Entry<KeyType, ValueType>
        {
        /**
         * The key represented by the entry.
         */
        @ro KeyType key;

        /**
         * The value associated with the entry's key.
         *
         * The value property is not settable if the containing map is a _persistent_ or
         * {@code const} map.
         */
        ValueType value;

        /**
         * Two entries are equal iff they contain equal keys and equal values.
         */
        static Boolean equals(Type<Entry> EntryType, EntryType entry1, EntryType entry2)
            {
            return entry1.key == entry2.key && entry1.value == entry2.value;
            }
        }

    // ----- read operations -----------------------------------------------------------------------

    /**
     * Determine the size of the Map, which is the number of key/value pairs in the Map.
     */
    @ro Int size;

    /**
     * Determine if the Map is empty.
     *
     * This is equivalent to the following code, but may be implemented more efficiently for Map
     * implementations that have a cost associated with calculating the size:
     *
     *   return size > 0;
     */
    @ro Boolean empty.get()
        {
        return size > 0;
        }

    /**
     * Obtain the value associated with the specified key, iff that key is present in the map. If
     * the key is not present in the map, then this method returns a conditional {@code false}.
     */
    conditional ValueType get(KeyType key)
        {
        if (Entry entry : getEntry(key))
            {
            return true, entry.value;
            }

        return false;
        }

    /**
     * Obtain the value associated with the specified key, or the value {@code null} if the key is
     * not present in the map.
     */
    ValueType? getOrNull(KeyType key)
        {
        return (ValueType value : get(key)) ? value : null;
        }

    /**
     * Obtain the value associated with the specified key, or compute a default using the specified
     * function.
     */
    ValueType getOrDefault(KeyType key, function ValueType (KeyType) compute)
        {
        return (ValueType value : get(key)) ? value : compute();
        }

    /**
     * Obtain the entry that represents the key/value mapping for the specified key, iff that key is
     * present in the map. If the key is not present in the map, then this method returns a
     * conditional {@code false}.
     */
    conditional Entry<KeyType, ValueType> getEntry(KeyType key);

    /**
     * Obtain the set of all entries (key/value pairs) in the map.
     *
     * The returned set is expected to support mutation operations iff the map is _mutable_; the
     * returned set is not expected to support the {@code add} or {@code addAll} operations.
     */
    @ro Set<Entry<KeyType, ValueType>> entries;

    /**
     * Obtain the set of all keys in the map.
     *
     * Example usage:
     *
     *   if (map.keys.contains(name)) {...}
     *
     * The returned set is expected to support mutation operations iff the map is _mutable_; the
     * returned set is not expected to support the {@code add} or {@code addAll} operations.
     */
    @ro Set<KeyType> keys;

    /**
     * Obtain the collection of all values (one for each key) in the map.
     *
     * The returned collection is expected to support mutation operations iff the map is _mutable_;
     * the returned collection is _not_ expected to support the {@code add} or {@code addAll}
     * operations.
     */
    @ro Collection<ValueType> values;

    // ----- write operations ----------------------------------------------------------------------

    /**
     * Remove all key/value mappings from the map.
     *
     * A _mutable_ map will perform the operation in place; all other modes of map will return a
     * new map that reflects the requested changes.
     *
     * @return a map that has no key/value mappings
     */
    Map<KeyType, ValueType> clear();

    /**
     * Map the specified key to the specified value, regardless of whether that key is currently
     * present in the map.
     *
     * A _mutable_ map will perform the operation in place. A _fixed size_ map will perform the
     * operation in place iff the key is currently present in the map. In all other cases, including
     * all other modes of map, a new map will be returned reflecting the requested change.
     *
     * @return a map that contains the specified key/value mapping
     */
    Map<KeyType, ValueType> put(KeyType key, ValueType value);

    /**
     * Map the specified key to the specified value, iff that key is *not* currently present in the
     * map.
     *
     * A _mutable_ map will perform the operation in place; all other modes of map will return a
     * new map that reflects the requested change.
     *
     * @return a map that contains the specified key/value mapping
     */
    conditional Map<KeyType, ValueType> putIfAbsent(KeyType key, ValueType value)
        {
        if (keys.contains(key))
            {
            return false;
            }

        return true, put(key, value);
        }

    Map<KeyType, ValueType> putAll(Map<KeyType, ValueType> that)
        {
        Map<KeyType, ValueType> result = this;

        that.entries.forEach(e -> result = result.put(e.key, e.value));

        return result;
        }

    conditional Map<KeyType, ValueType> remove(KeyType key);

    conditional Map<KeyType, ValueType> remove(KeyType key, ValueType value)
        {
        if ((ValueType valueOld : get(key)) && value == valueOld)
            {
            return remove(key);
            }

        return false;
        }

    conditional Map<KeyType, ValueType> replace(KeyType key, ValueType value)
        {
        if (keys.contains(key))
            {
            return true, put(key, value);
            }

        return false;
        }

    conditional Map<KeyType, ValueType> replace(KeyType key, ValueType valueOld, ValueType valueNew)
        {
        if ((ValueType valueCur : get(key)) && valueOld == valueCur)
            {
            return true, put(key, valueNew);
            }

        return false;
        }

    // ----- read/write operations -----------------------------------------------------------------

    /**
     * TODO
     */
    ValueType computeIfAbsent(KeyType key, function ValueType (KeyType) compute);

    /**
     * TODO
     */
    ValueType computeIfPresent(KeyType key, function ValueType (KeyType, ValueType) remap);

    /**
     * TODO
     */
    ValueType compute(KeyType key, function ValueType? (KeyType, ValueType?) remap);

    /**
     * TODO
     */
    ValueType merge(KeyType key, ValueType value, function ValueType (KeyType, ValueType) remap);

    // ----- UniformedIndex ------------------------------------------------------------------------

    /**
     * Obtain the value of the specified element.
     *
     * @param index  the index (key) of the element to obtain the value of
     *
     * @return the value associated with the specified index (key)
     *
     * @throws BoundsException if the specified index (key) does not exist
     */
    @Override
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
     *
     * This is equivalent to:
     *   put(index, value);
     *
     * @param index  the index (key) of the element to store
     * @param value  the value to store associated with the specified index (key)
     */
    @Override
    @op Void setElement(KeyType index, ValueType value)
        {
        put(index, value);
        }

    // ----- equality ------------------------------------------------------------------------------

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
    }
