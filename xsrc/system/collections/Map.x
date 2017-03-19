/**
 * A Map is a _dictionary_ data structure that stores _values_, each identified by a _key_.
 *
 * The Map is one of the most commonly used data structures, because it allows information to be
 * easily _related to_ other information. As with many data structures, it is expected that
 * implementations will support one or more of these four modes, whose general behaviors are
 * defined as:
 * * A *mutable* map is one that allows items to be added and removed, and whose values for
 *   particular keys can be replaced, and whose contents are generally not required to be immutable.
 *   If an implementation provides support for more than one mode, including a *mutable* mode, then
 *   it should implement the {@link MutableAble} interface.
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
         * True iff the entry is existent in its map. An entry does not exist in its map before its
         * {@link value} is assigned, or after a call to {@link remove}.
         */
        @ro Boolean exists;

        /**
         * The value associated with the entry's key.
         *
         * The value property is not settable if the containing map is a _persistent_ or
         * {@code const} map.
         *
         * If the entry does not {@link exist}, then the value is not readable; an attempt to get
         * the value of an will raise a {@link BoundsException}
         *
         * @throws BoundsException if an attempt is made to read the value of the entry when {@link
         *         exists} is false
         * @throws ReadOnlyException if an attempt is made to write the value of the entry when
         *         the map is _persistent_ or {@code const}
         */
        ValueType value;

        /**
         * Remove the entry from its map.
         *
         * The entry is removable if the containing map is _mutable_; it is not removable if the
         * containing map is _fixed size_, _persistent_, or {@code const}.
         *
         * @throws ReadOnlyException if the map is _fixed size_, _persistent_, or {@code const}
         */
        Void remove();

        /**
         * Two entries are equal iff they contain equal keys and equal values.
         */
        static Boolean equals(Type<Entry> EntryType, EntryType entry1, EntryType entry2)
            {
            return entry1.key == entry2.key
                && entry1.exists && entry2.exists
                && entry1.value == entry2.value;
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
     *
     * @param key  the key to look up in the map
     *
     * @return a conditional true and the value for the associated key if it exists in the map;
     *         otherwise a conditional false
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
     *
     * @param key  the key to look up in the map
     *
     * @return the value for the associated key if it exists in the map; otherwise null
     */
    ValueType? getOrNull(KeyType key)
        {
        return (ValueType value : get(key)) ? value : null;
        }

    /**
     * Obtain the value associated with the specified key, or if the key does not already exist in
     * the map, compute a default value using the specified function. Note that his method does not
     * store the result of the computation; it simply returns the computed value. To store the
     * result, use {@link computeIfAbsent} instead.
     *
     * @param key      specifies the key that may or may not already be present in the map
     * @param compute  the function that will be called iff the key does not exist in the map
     *
     * @return the value for the specified key if it exists in the map; otherwise, the result of the
     *         specified function
     */
    ValueType getOrDefault(KeyType key, function ValueType () compute)
        {
        return (ValueType value : get(key)) ? value : compute();
        }

    /**
     * Obtain the entry that represents the key/value mapping for the specified key, iff that key is
     * present in the map. If the key is not present in the map, then this method returns a
     * conditional {@code false}.
     *
     * @param key  the key to look up in the map
     *
     * @return a conditional true and the {@link Entry} for the associated key if it exists in the
     *         map; otherwise a conditional false
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
     * Store a mapping of the specified key to the specified value, regardless of whether that key
     * is already present in the map.
     *
     * A _mutable_ map will perform the operation in place. A _fixed size_ map will perform the
     * operation in place iff the key is currently present in the map. In all other cases, including
     * all other modes of map, a new map will be returned reflecting the requested change.
     *
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key
     *
     * @return the resultant map, which is the same as {@code this} for a mutable map
     */
    Map<KeyType, ValueType> put(KeyType key, ValueType value);

    /**
     * Store in this map each of the mappings of key and values specified in another map, regardless
     * of whether those keys and/or values are already present in this map.
     *
     * A _mutable_ map will perform the operation in place. A _fixed size_ map will perform the
     * operation in place iff all of the keys are already present in the map. In all other cases,
     * including all other modes of map, a new map will be returned reflecting the requested change.
     *
     * @param that  another map containing keys and associated values to put into this map
     *
     * @return the resultant map, which is the same as {@code this} for a mutable map
     */
    Map<KeyType, ValueType> putAll(Map<KeyType, ValueType> that);

    /**
     * Remove the specified key and any associated value from this map.
     *
     * @param key  the key to remove from this map
     *
     * @return the resultant map, which is the same as {@code this} for a mutable map
     */
    Map<KeyType, ValueType> remove(KeyType key);

    /**
     * Remove all key/value mappings from the map.
     *
     * A _mutable_ map will perform the operation in place; all other modes of map will return a
     * new map that reflects the requested changes.
     *
     * @return the resultant map, which is the same as {@code this} for a mutable map
     */
    Map<KeyType, ValueType> clear();

    /**
     * Map the specified key to the specified value, iff that key is *not* currently present in the
     * map.
     *
     * A _mutable_ map will perform the operation in place; all other modes of map will return a
     * new map that reflects the requested change.
     *
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key iff the key does not already
     *               exist in the map
     *
     * @return a conditional true and the resultant map if the key did not previously exist in the
     *         map; otherwise a conditional false
     */
    conditional Map<KeyType, ValueType> putIfAbsent(KeyType key, ValueType value)
        {
        if (keys.contains(key))
            {
            return false;
            }

        return true, put(key, value);
        }

    /**
     * Store the specified new value in the map associated with the specified key, iff that key is
     * currently associated with the specified old value.
     *
     * @param key       the key to store in the map
     * @param valueOld  the value to verify is currently associated with the specified key
     * @param valueNew  the value to associate with the specified key
     *
     * @return a conditional true and the resultant map if the key did exist in the map and was
     *         associated with the previous value; otherwise a conditional false
     */
    conditional Map<KeyType, ValueType> replace(KeyType key, ValueType valueOld, ValueType valueNew)
        {
        if ((ValueType valueCur : get(key)) && valueOld == valueCur)
            {
            return true, put(key, valueNew);
            }

        return false;
        }

    /**
     * Remove the specified key and the associated value from this map, iff the key exists in this
     * map and is associated with the specified value.
     *
     * @param key    the key to remove from the map
     * @param value  the value to verify is currently associated with the specified key
     *
     * @return a conditional true and the resultant map if the key did in the map and was
     *         associated with the specified value; otherwise a conditional false
     */
    conditional Map<KeyType, ValueType> remove(KeyType key, ValueType value)
        {
        if ((ValueType valueOld : get(key)) && value == valueOld)
            {
            return remove(key);
            }

        return false;
        }

    // ----- read/write operations -----------------------------------------------------------------

    /**
     * Apply the specified function to the entry for the specified key. If that key does not exist
     * in the map, an Entry is provided to it whose {@link Entry.exists exists} property is false;
     * setting the value of the entry will cause the entry to _appear in_ the map, which is to say
     * that the map will contain an entry for that key with that value. Similarly, calling {@link
     * Entry.remove remove} on the entry will ensure that the key and any associated value are _not_
     * present in the map.
     *
     * @param key      specifies which entry to process
     * @param compute  the function that will operate against the entry
     *
     * @return the result of the specified function
     *
     * @throws ReadOnlyException if an attempt is made to add or remove an entry in a map that is
     *         not _mutable_, or to modify an entry in a map that is not _mutable_ or _fixed size_
     */
    <ResultType> ResultType process(KeyType key,
            function ResultType (Entry<KeyType, ValueType>) compute);

    /**
     * Apply the specified function to the entry for the specified key, iff such an entry exists in
     * the map.
     *
     * @param key      specifies which entry to process
     * @param compute  the function that will operate against the entry, iff the entry already
     *                 exists in the map
     *
     * @return a conditional true and the result of the specified function iff the entry exists;
     *         otherwise a conditional false
     *
     * @throws ReadOnlyException if an attempt is made to modify an entry in a map that is not
     *         _mutable_ or _fixed size_
     */
    <ResultType> conditional ResultType processIfPresent(KeyType key,
            function ResultType (Entry<KeyType, ValueType>) compute)
        {
        return process(key, entry ->
            {
            if (entry.exists)
                {
                return true, compute(entry);
                }
            return false;
            });
        }

    /**
     * Obtain the existing value for the specified key, or if the key does not already exist in the
     * map, create a new entry in the map containing the specified key and the result of the
     * specified function.
     *
     * @param key      specifies the key that may or may not already be present in the map
     * @param compute  the function that will be called iff the key does not already exist in the
     *                 map, and which will return the new value to associate with that key
     *
     * @return the value for the specified key, which may have already existed in the map, or may
     *         have just been calculated by the specified function and placed into the map
     *
     * @throws ReadOnlyException if an attempt is made to add an entry in a map that is not
     *         _mutable_
     */
    ValueType computeIfAbsent(KeyType key, function ValueType () compute)
        {
        return process(key, entry ->
            {
            if (!entry.exists)
                {
                entry.value = compute();
                }
            return entry.value;
            });
        }

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
     *
     * @throws BoundsException if the specified index (key) does not exist and the map is not
     *         of the _mutable_ variety
     * @throws ReadOnlyException if the map is of the _persistent_ or {@code const} variety
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

        for (Map.Entry<MapType.KeyType, MapType.ValueType> entry : map1)
            {
            if (!(MapType.ValueType value : map2.get(entry.key) && entry.value == value))
                {
                return false;
                }
            }

        return true;
        }

    // ----- values collection implementations -----------------------------------------------------

    /**
     * An implementation of the Collection for the {@link Map.values} property that delegates back
     * to the map and to the map's {@link Map.entries entries}.
     */
    class EntryBasedValuesCollection<KeyType, ElementType>
            implements Collection<ElementType>
        {
        construct ValuesCollection(Map<KeyType, ElementType> map)
            {
            this.map = map;
            }

        protected/private Map<KeyType, ElementType> map;

        @Override
        Int size.get()
            {
            return map.size;
            }

        @Override
        Boolean empty.get()
            {
            return map.empty;
            }

        @Override
        Iterator<ElementType> iterator()
            {
            return new Iterator()
                {
                Iterator entryIterator = map.entries.iterator(); // TODO verify this is private

                conditional ElementType next()
                    {
                    if (Entry<KeyType, ElementType> entry : entryIterator)
                        {
                        return true, entry.value;
                        }
                    return false;
                    }
                };
            }

        @Override
        Collection<ElementType> remove(ElementType value)
            {
            map.entries.iterator().untilAny(entry ->
                {
                if (entry.value == value)
                    {
                    entry.remove();
                    return true;
                    }
                return false;
                });

            return this;
            }

        @Override
        Collection<ElementType> removeIf(function Boolean (ElementType) shouldRemove)
            {
            map.entries.removeIf(entry -> shouldRemove(entry.value));
            }

        @Override
        Collection<ElementType> clear()
            {
            map.clear();
            return this;
            }
        }

    /**
     * An implementation of the Collection for the {@link Map.values} property that delegates back
     * to the map and to the map's {@link Map.keys keys}.
     */
    class KeyBasedValuesCollection<KeyType, ElementType>
            implements Collection<ElementType>
        {
        construct ValuesCollection(Map<KeyType, ElementType> map)
            {
            this.map = map;
            }

        protected/private Map<KeyType, ElementType> map;

        @Override
        Int size.get()
            {
            return map.size;
            }

        @Override
        Boolean empty.get()
            {
            return map.empty;
            }

        @Override
        Iterator<ElementType> iterator()
            {
            return new Iterator()
                {
                Iterator keyIterator = map.keys.iterator(); // TODO verify this is private

                conditional ElementType next()
                    {
                    if (KeyType key : keyIterator)
                        {
                        return map.get(key);
                        }

                    return false;
                    }
                };
            }

        @Override
        Collection<ElementType> remove(ElementType value)
            {
            map.keys.iterator().untilAny(key ->
                {
                if (ElementType keyvalue : map.get(key) && keyvalue == value)
                    {
                    assert map == map.remove(key);
                    return true;
                    }
                return false;
                });

            return this;
            }

        @Override
        Collection<ElementType> removeIf(function Boolean (ElementType) shouldRemove)
            {
            map.keys.removeIf(key ->
                {
                assert ElementType keyvalue : map.get(key);
                return shouldRemove(keyvalue);
                });
            }

        @Override
        Collection<ElementType> clear()
            {
            map.clear();
            return this;
            }
        }
    }
