/**
 * A Map is a _dictionary_ data structure that stores _values_, each identified by a _key_; the
 * combination of a key and its value is an _entry_.
 *
 * The Map is one of the most commonly used data structures, because it allows information to be
 * easily _related to_ other information.
 */
interface Map<KeyType, ValueType>
        extends VariablyMutable
        extends Stringable
    {
    /**
     * Describes the mutability of this Map:
     *
     * * A **mutable** map is one that allows items to be added and removed, and whose values for
     *   particular keys can be replaced, and whose contents are generally not required to be
     *   immutable. If an implementation provides support for more than one mode, including a
     *   **mutable** mode, then it should implement the [MutableAble] interface.
     * * A **fixed size** map is one that does not allow items to be added or removed, but whose
     *   values for particular keys can be replaced, and whose contents are generally not required
     *   to be immutable. If an implementation provides support for more than one mode, including
     *   a **fixed size** mode, then it should implement the [FixedSizeAble] interface.
     * * A **persistent** map is one that does not allow items to be added or removed, whose values
     *   for particular keys can not be replaced, but whose contents are generally not required to
     *   be immutable. Requesting a persistent map to add, remove, or modify its contents will
     *   result in a new persistent map as a result of the request. If an implementation provides
     *   support for more than one mode, including a **persistent** mode, then it should implement
     *   the [PersistentAble] interface.
     * * A **const** map is one that is immutable, whose size and contents are immutable, and which
     *   provides a new **const** map as the result of any mutating request. If an implementation
     *   provides support for more than one mode, including a **const** mode, then it should
     *   implement the [ConstAble] interface.
     */
    @Override
    @RO Mutability mutability;


    // ----- read operations -----------------------------------------------------------------------

    /**
     * Determine the size of the Map, which is the number of entries (key/value pairs) in the Map.
     */
    @RO Int size;

    /**
     * Determine if the Map is empty.
     *
     * This is equivalent to checking `size == 0`, but may be implemented more efficiently for Map
     * implementations that have a cost associated with calculating the size.
     */
    @RO Boolean empty.get()
        {
        return size == 0;
        }

    /**
     * Check if this map contains the specified key.
     *
     * @param key  specifies the key that may or may not already be present in the map
     *
     * @return the True iff the specified key exists in the map
     */
    Boolean contains(KeyType key)
        {
        return get(key);
        }

    /**
     * Obtain the value associated with the specified key, iff that key is present in the map. If
     * the key is not present in the map, then this method returns a conditional `False`.
     *
     * @param key  the key to look up in the map
     *
     * @return a conditional True and the value associated with the specified key if it exists in
     *         the map; otherwise a conditional False
     */
    conditional ValueType get(KeyType key);

    /**
     * Obtain the value associated with the specified key, or the value `Null` if the key is
     * not present in the map.
     *
     * @param key  the key to look up in the map
     *
     * @return the value for the associated key if it exists in the map; otherwise Null
     */
    @Op("[]")
    ValueType? getOrNull(KeyType key)
        {
        if (ValueType value : get(key))
            {
            return value;
            }

        return Null;
        }

    /**
     * Obtain the value associated with the specified key, or if the key does not already exist in
     * the map, return the specified default value. Note that his method does not store the default
     * if the key is missing; it simply returns it. To store the result, use [computeIfAbsent]
     * instead.
     *
     * @param key     specifies the key that may or may not already be present in the map
     * @param dftval  the default value
     *
     * @return the value for the specified key if it exists in the map; otherwise, the default value
     */
    ValueType getOrDefault(KeyType key, ValueType dftval)
        {
        if (ValueType value : get(key))
            {
            return value;
            }

        return dftval;
        }

    /**
     * Obtain the value associated with the specified key, or if the key does not already exist in
     * the map, compute a default value using the specified function. Note that his method does
     * **not** store the result of the computation; it simply returns the computed value. To store
     * the result, use [computeIfAbsent] instead.
     *
     * @param key      specifies the key that may or may not already be present in the map
     * @param compute  the function that will be called iff the key does not exist in the map
     *
     * @return the value associated with the specified key iff the key exists in the map; otherwise,
     *         the result from the provided function
     */
    ValueType getOrCompute(KeyType key, function ValueType () compute)
        {
        if (ValueType value : get(key))
            {
            return value;
            }

        return compute();
        }

    /**
     * Obtain the set of all keys in the map.
     *
     * Example usage:
     *
     *   if (map.keys.contains(name)) {...}
     *
     * The returned set is expected to support mutation operations iff the map is _mutable_; the
     * returned set is not expected to support the `add` or `addAll` operations.
     */
    @RO Set<KeyType> keys;

    /**
     * Obtain the collection of all values (one for each key) in the map.
     *
     * The returned collection is expected to support mutation operations iff the map is _mutable_;
     * the returned collection is _not_ expected to support the `add` or `addAll` operations.
     */
    @RO Collection<ValueType> values;

    /**
     * Obtain the set of all entries (key/value pairs) in the map.
     *
     * The returned set is expected to support mutation operations iff the map is _mutable_.
     */
    @RO Set<Entry> entries;


    // ----- write operations ----------------------------------------------------------------------

    /**
     * Store a mapping of the specified key to the specified value, regardless of whether that key
     * is already present in the map.
     *
     * A _mutable_ map will perform the operation in place. A _fixed size_ map will perform the
     * operation in place iff the key is currently present in the map. A _persistent_ or _const_
     * map will return a new map reflecting the requested change.
     *
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key
     *
     * @return the resultant map, which is the same as `this` for a mutable map
     */
    conditional Map put(KeyType key, ValueType value)
        {
        TODO entry addition and modification is not supported
        }

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
     * @return the resultant map, which is the same as `this` for a mutable map
     */
    conditional Map putAll(Map! that)
        {
        Boolean modified = False;
        Map     result   = this;
        for (Entry entry : that.entries)
            {
            if (result : result.put(entry.key, entry.value))
                {
                modified = True;
                }
            }
        return modified, result;
        }

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
     * @return a conditional True and the resultant map if the key did not previously exist in the
     *         map; otherwise a conditional False
     */
    conditional Map putIfAbsent(KeyType key, ValueType value)
        {
        if (keys.contains(key))
            {
            return False;
            }

        return put(key, value);
        }

    /**
     * Store the specified new value in the map associated with the specified key, iff that key is
     * currently associated with the specified old value.
     *
     * @param key       the key to store in the map
     * @param valueOld  the value to verify is currently associated with the specified key
     * @param valueNew  the value to associate with the specified key
     *
     * @return a conditional True and the resultant map if the key did exist in the map and was
     *         associated with the previous value; otherwise a conditional False
     */
    conditional Map replace(KeyType key, ValueType valueOld, ValueType valueNew)
        {
        if (valueOld != valueNew)
            {
            if (ValueType valueCur : get(key))
                {
                if (valueOld == valueCur)
                    {
                    return put(key, valueNew);
                    }
                }
            }

        return False;
        }

    /**
     * Remove the specified key and any associated value from this map.
     *
     * @param key  the key to remove from this map
     *
     * @return the resultant map, which is the same as `this` for a mutable map
     */
    conditional Map remove(KeyType key)
        {
        TODO entry removal is not supported
        }

    /**
     * Remove the specified key and the associated value from this map, iff the key exists in this
     * map and is associated with the specified value.
     *
     * @param key    the key to remove from the map
     * @param value  the value to verify is currently associated with the specified key
     *
     * @return the resultant Map if the key did exist in the Map, was associated with the specified
     *         value, and was modified in the returned Map
     */
    conditional Map remove(KeyType key, ValueType value)
        {
        if (ValueType valueOld : get(key))
            {
            if (value == valueOld)
                {
                return remove(key);
                }
            }

        return False;
        }

    /**
     * Remove all key/value mappings from the map.
     *
     * A _mutable_ map will perform the operation in place; all other modes of map will return a
     * new map that reflects the requested changes.
     *
     * @return the resultant map, which will be `this` for a mutable map
     */
    conditional Map clear();


    // ----- Entry operations -----------------------------------------------------------

    /**
     * Apply the specified function to the entry for the specified key. If that key does not exist
     * in the map, an Entry is provided to it whose [Entry.exists] property is False;
     * setting the value of the entry will cause the entry to _appear in_ the map, which is to say
     * that the map will contain an entry for that key with that value. Similarly, calling {@link
     * Entry.remove remove} on the entry will ensure that the key and any associated value are _not_
     * present in the map.
     *
     * @param key      specifies which entry to process
     * @param compute  the function that will operate against the Entry
     *
     * @return the result of the specified function
     *
     * @throws ReadOnly if an attempt is made to add or remove an entry in a map that is not
     *                  _mutable_, or to modify an entry in a map that is not _mutable_ or
     *                  _fixed size_
     */
    <ResultType> ResultType process(KeyType key, function ResultType (Entry) compute);

    /**
     * Apply the specified function to the Entry objects for the specified keys.
     *
     * @param keys     specifies which keys to process
     * @param compute  the function that will operate against each Entry
     *
     * @return a Map containing the results from the specified function applied against the entries
     *         for the specified keys
     *
     * @throws ReadOnly if an attempt is made to add or remove an entry in a map that is not
     *                  _mutable_, or to modify an entry in a map whose [mutability] is not
     *                  `Mutable` or `Fixed`
     */
    <ResultType> Map<KeyType, ResultType> processAll(Collection<KeyType> keys,
            function ResultType (Entry) compute)
        {
        ListMap<KeyType, ResultType> result = new ListMap(keys.size);
        for (KeyType key : keys)
            {
            result.put(key, process(key, compute));
            }
        return result;
        }

    /**
     * Apply the specified function to the entry for the specified key, iff such an entry exists in
     * the map.
     *
     * @param key      specifies which entry to process
     * @param compute  the function that will operate against the Entry, iff the entry
     *                 already exists in the map
     *
     * @return a conditional True and the result of the specified function iff the entry exists;
     *         otherwise a conditional False
     *
     * @throws ReadOnly if an attempt is made to modify an entry in a map that is not
     *                  _mutable_ or _fixed size_
     */
    <ResultType> conditional ResultType processIfPresent(
            KeyType key, function ResultType (Entry) compute)
        {
        // this implementation can be overridden to combine the contains() and process() into
        // a single step
        if (contains(key))
            {
            return True, process(key, entry ->
                {
                assert entry.exists;
                return compute(entry);
                });
            }
        else
            {
            return False;
            }
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
     * @throws ReadOnly if an attempt is made to add an entry in a map that is not _mutable_
     */
    ValueType computeIfAbsent(KeyType key, function ValueType () compute)
        {
        return process(key, entry ->
            {
            if (entry.exists)
                {
                return entry.value;
                }

            if (mutability != Mutable)
                {
                throw new ReadOnly();
                }

            ValueType value = compute();
            entry.value = value;
            return value;
            });
        }


    // ----- Entry interface -----------------------------------------------------------------------

    /**
     * A Map Entry represents a single mapping from a particular key to its value. The Entry
     * interface is designed to allow a Map implementor to re-use a temporary Entry instance to
     * represent a _current_ Entry, for example during iteration; this allows a single Entry object
     * to represent every Entry in the Map. As a consequence of this decision, an Entry consumer
     * *must* call [reify] on each entry that will be held beyond the scope of the current
     * iteration.
     *
     * When used for A consumer can determine whether an entry exists (is present in a map), to cause an entry to exist (if it
     * did not exist) by assigning a value, to change the value of an entry that does exist, and to
     * remove the entry if it exists.
     */
    interface Entry
        {
        /**
         * The key represented by the entry.
         */
        @RO KeyType key;

        /**
         * True iff the entry is existent in its map. An entry does not exist in its map before its
         * [value] has been assigned, or after a call to [remove].
         */
        @RO Boolean exists;

        /**
         * The value associated with the entry's key.
         *
         * The value property is not settable and the entry is not removable if the containing map
         * is _persistent_.
         *
         * If the entry does not [exist](exists), then the value is not readable; an attempt to get
         * the value of an will raise an `OutOfBounds`
         *
         * @throws OutOfBounds if an attempt is made to read the value of the entry when {@link
         *                     exists} is False
         * @throws ReadOnly    if an attempt is made to write the value of the entry when
         *                     the map is _persistent_ or `const`
         */
        ValueType value;

        /**
         * Remove the entry from its map.
         *
         * The entry is removable if the containing map is _mutable_; it is not removable if the
         * containing map is _fixed size_, _persistent_, or `const`.
         *
         * @throws ReadOnly if the map is _fixed size_, _persistent_, or `const`
         */
        void remove();

        /**
         * If the entry is a temporary object, for example an entry that can be re-used to represent
         * multiple logical entries within an entry iterator, then obtain a reference to the same
         * entry that is _not_ temporary, allowing the resulting reference to be held indefinitely.
         *
         * Holding the Entry will likely prevent the garbage-collection of the Map.
         *
         * @return an Entry object that can be retained indefinitely; changes to the values in the
         *         map may or may not be reflected in the returned Entry
         */
        Entry reify()
            {
            return this;
            }

        /**
         * Two entries are equal iff they contain equal keys and equal values (or neither exists).
         */
        static <CompileType extends Entry> Boolean equals(CompileType entry1, CompileType entry2)
            {
            return entry1.key == entry2.key
                && entry1.exists ?  entry2.exists && entry1.value == entry2.value
                                 : !entry2.exists;
            }
        }


    // ----- equality ------------------------------------------------------------------------------

    /**
     * Two maps are equal iff they are they contain the same keys, and the value associated with
     * each key in the first map is equal to the value associated with the same key in the second
     * map.
     */
    static <CompileType extends Map> Boolean equals(CompileType map1, CompileType map2)
        {
        if (map1.size != map2.size)
            {
            return False;
            }

        for (CompileType.KeyType key1, CompileType.ValueType value1 : map1)
            {
            if (CompileType.ValueType value2 : map2.get(key1))
                {
                if (value2 == value1)
                    {
                    continue;
                    }
                }
            return False;
            }

        return True;
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return (3 * size)   // allow for "[]", for "=" on each entry, and ", " between each entry
                + estimateStringLength(keys)
                + estimateStringLength(values);
        }

    // TODO GG move this method to be inside of the above method
    private static Int estimateStringLength(Collection coll)
        {
        Int capacity = 0;
        if (coll.is(Collection<Stringable>))
            {
            for (coll.ElementType element : coll)
                {
                capacity += element.estimateStringLength();
                }
            }
        else
            {
            for (coll.ElementType element : coll)
                {
                if (element.is(Stringable))
                    {
                    capacity += element.estimateStringLength();
                    }
                else
                    {
                    // completely arbitrary estimate
                    capacity += 8;
                    }
                }
            }
        return capacity;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add('[');

        if (KeyType.is(Type<Stringable>) && ValueType.is(Type<Stringable>))
            {
            Append:
            for (Entry entry : entries)
                {
                if (!Append.first)
                    {
                    appender.add(", ");
                    }
                entry.key.appendTo(appender);
                appender.add('=');
                entry.value.appendTo(appender);
                }
            }
        else
            {
            Append:
            for (Entry entry : entries)
                {
                if (!Append.first)
                    {
                    appender.add(", ");
                    }
                KeyType key = entry.key;
                if (key.is(Stringable))
                    {
                    key.appendTo(appender);
                    }
                else
                    {
                    appender.add(key.to<String>());
                    }

                appender.add('=');

                ValueType value = entry.value;
                if (value.is(Stringable))
                    {
                    value.appendTo(appender);
                    }
                else
                    {
                    appender.add(value.to<String>());
                    }
                }
            }
        appender.add(']');
        }
    }
