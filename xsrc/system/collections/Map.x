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
        extends UniformIndexed<KeyType, ValueType>
        extends ConstAble
    {
    // ----- Entry interface -----------------------------------------------------------------------

    /**
     * A Map Entry represents a single mapping from a particular key to its value. The Entry
     * interface is designed to allow a Map implementor to re-use a temporary Entry instance to
     * represent a _current_ Entry, for example during iteration; this allows a single Entry object
     * to represent every Entry in the Map. As a consequence of this approach, an Entry consumer
     * *must* call {@link reify} on each entry that will be held beyond the scope of the current
     * iteration.
     */
    interface Entry<KeyType, ValueType>
        {
        /**
         * The key represented by the entry.
         */
        @RO KeyType key;

        /**
         * The value associated with the entry's key.
         *
         * The value property is not settable if the containing map is a _persistent_ or
         * {@code const} map.
         *
         * @throws ReadOnlyException if an attempt is made to write the value of the entry when
         *         the map is _persistent_ or {@code const}
         */
        ValueType value;

        /**
         * If the entry is a temporary object, for example an entry that can be re-used to represent
         * multiple logical entries within an entry iterator, then obtain a reference to the same
         * entry that is _not_ temporary, allowing the resulting reference to be held indefinitely.
         *
         * @return an Entry object that can be retained indefinitely; changes to the values in the
         *         map may or may not be reflected in the returned Entry
         */
        Entry<KeyType, ValueType> reify()
            {
            return this;
            }

        /**
         * Two entries are equal iff they contain equal keys and equal values.
         */
        static <CompileType extends Entry> Boolean equals(CompileType entry1, CompileType entry2)
            {
            return entry1.key == entry2.key
                && entry1.value == entry2.value;
            }
        }

    /**
     * A ProcessableEntry represents an extension to the Entry interface that allows a consumer to
     * determine whether an entry exists (is present in a map), to cause an entry to exist (if it
     * did not exist) by assigning a value, to change the value of an entry that does exist, and to
     * remove the entry if it exists.
     */
    interface ProcessableEntry<KeyType, ValueType>
            extends Entry<KeyType, ValueType>
        {
        /**
         * True iff the entry is existent in its map. An entry does not exist in its map before its
         * {@link value} is assigned, or after a call to {@link remove}.
         */
        @RO Boolean exists;

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
        @Override
        ValueType value;

        /**
         * Remove the entry from its map.
         *
         * The entry is removable if the containing map is _mutable_; it is not removable if the
         * containing map is _fixed size_, _persistent_, or {@code const}.
         *
         * @throws ReadOnlyException if the map is _fixed size_, _persistent_, or {@code const}
         */
        void remove();

        /**
         * If the entry is a temporary object, for example an entry that can be re-used to represent
         * multiple logical entries within an entry iterator, then obtain a reference to the same
         * entry that is _not_ temporary, allowing the resulting reference to be held indefinitely.
         *
         * Note that a ProcessableEntry is defined as returning only an Entry, not a
         * ProcessableEntry.
         *
         * @return an Entry object that can be retained indefinitely; changes to the values in the
         *         map may or may not be reflected in the returned Entry
         */
        @Override
        Entry<KeyType, ValueType> reify()
            {
            return this;
            }
        }

    // ----- read operations -----------------------------------------------------------------------

    /**
     * Determine the size of the Map, which is the number of key/value pairs in the Map.
     */
    @RO Int size;

    /**
     * Determine if the Map is empty.
     *
     * This is equivalent to the following code, but may be implemented more efficiently for Map
     * implementations that have a cost associated with calculating the size:
     *
     *   return size > 0;
     */
    @RO Boolean empty.get()
        {
        return size > 0;
        }

    /**
     * Obtain the Entry for the specified key, if the entry exists in the Map.
     *
     * @param key  the key to find the entry for
     *
     * @return the conditional Entry if the key exists in the Map
     */
    conditional Entry<KeyType, ValueType> getEntry(KeyType key);

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
        if (Entry<KeyType, ValueType> entry : getEntry(key))
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
        if (ValueType value : get(key))
            {
            return value;
            }

        return null;
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
        if (ValueType value : get(key))
            {
            return value;
            }

        return compute();
        }

    /**
     * Check if this map contains the specified key.
     *
     * @param key      specifies the key that may or may not already be present in the map
     *
     * @return the true iff the specified key exists in the map
     */
    Boolean containsKey(KeyType key)
        {
        return keys.contains(key);
        }

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
    @RO Set<KeyType> keys;

    /**
     * Obtain the set of all entries (key/value pairs) in the map.
     *
     * The returned set is expected to support mutation operations iff the map is _mutable_; the
     * returned set is not expected to support the {@code add} or {@code addAll} operations.
     */
    @RO Set<Entry<KeyType, ValueType>> entries;

    /**
     * Obtain the collection of all values (one for each key) in the map.
     *
     * The returned collection is expected to support mutation operations iff the map is _mutable_;
     * the returned collection is _not_ expected to support the {@code add} or {@code addAll}
     * operations.
     */
    @RO Collection<ValueType> values;

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
    Map<KeyType, ValueType> putAll(Map!<KeyType, ValueType> that)
        {
        Map<KeyType, ValueType> result = this;
        for (Entry<KeyType, ValueType> entry : that.entries)
            {
            result = result.put(entry.key, entry.value);
            }
        return result;
        }

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
        if (ValueType valueCur : get(key))
            {
            if (valueOld == valueCur)
                {
                return true, put(key, valueNew);
                }
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
        if (ValueType valueOld : get(key))
            {
            if (value == valueOld)
                {
                return true, remove(key);
                }
            }

        return false;
        }

    // ----- ProcessableEntry operations -----------------------------------------------------------

    /**
     * Apply the specified function to the entry for the specified key. If that key does not exist
     * in the map, an Entry is provided to it whose {@link Entry.exists exists} property is false;
     * setting the value of the entry will cause the entry to _appear in_ the map, which is to say
     * that the map will contain an entry for that key with that value. Similarly, calling {@link
     * Entry.remove remove} on the entry will ensure that the key and any associated value are _not_
     * present in the map.
     *
     * @param key      specifies which entry to process
     * @param compute  the function that will operate against the ProcessableEntry
     *
     * @return the result of the specified function
     *
     * @throws ReadOnlyException if an attempt is made to add or remove an entry in a map that is
     *         not _mutable_, or to modify an entry in a map that is not _mutable_ or _fixed size_
     */
    <ResultType> ResultType process(KeyType key,
            function ResultType (ProcessableEntry<KeyType, ValueType>) compute);

    /**
     * Apply the specified function to the entry for the specified key, iff such an entry exists in
     * the map.
     *
     * @param key      specifies which entry to process
     * @param compute  the function that will operate against the ProcessableEntry, iff the entry
     *                 already exists in the map
     *
     * @return a conditional true and the result of the specified function iff the entry exists;
     *         otherwise a conditional false
     *
     * @throws ReadOnlyException if an attempt is made to modify an entry in a map that is not
     *         _mutable_ or _fixed size_
     */
    <ResultType> conditional ResultType processIfPresent(KeyType key,
            function ResultType (ProcessableEntry<KeyType, ValueType>) compute)
        {
        // this implementation can be overridden to combine the containsKey() and process() into
        // a single step
        if (containsKey(key))
            {
            return true, process(key, entry ->
                {
                assert entry.exists;
                return compute(entry);
                });
            }
        else
            {
            return false;
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
     * @throws ReadOnlyException if an attempt is made to add an entry in a map that is not
     *         _mutable_
     */
    ValueType computeIfAbsent(KeyType key, function ValueType () compute)
        {
        return process(key, entry ->
            {
            ValueType value;
            if (entry.exists)
                {
                value = entry.value;
                }
            else
                {
                value = compute();
                entry.value = value;
                }
            return value;
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
    @Op ValueType getElement(KeyType index)
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
    @Op void setElement(KeyType index, ValueType value)
        {
        // this must be overridden by map implementations that are not of the "mutable" variety
        Map<KeyType, ValueType> map    = this;
        Map<KeyType, ValueType> newMap = map.put(index, value);
        assert &map == &newMap;
        }

    // ----- ConstAble -----------------------------------------------------------------------------

    @Override
    immutable Map<KeyType, ValueType> ensureConst(Boolean inPlace = false)
        {
        if (inPlace)
            {
            return makeImmutable();
            }
        throw new UnsupportedOperationException();
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
            return false;
            }

        for (CompileType.KeyType key1, CompileType.ValueType value1 : map1)
            {
            if (ValueType value2 : map2.get(key1))
                {
                if (value2 == value1)
                    {
                    continue;
                    }
                }
            return false;
            }

        return true;
        }

    // ----- keys set implementations --------------------------------------------------------------

    /**
     * An implementation of the Set for the {@link Map.keys} property that delegates back
     * to the map and to the map's {@link Map.entries entries}.
     */
    class EntryBasedKeySet<KeyType>
            implements Set<KeyType>
        {
        @Override
        Int size.get()
            {
            return Map.this.size;
            }

        @Override
        Boolean empty.get()
            {
            return Map.this.empty;
            }

        @Override
        Iterator<KeyType> iterator()
            {
            return new Iterator()
                {
                Iterator entryIterator = Map.this.entries.iterator();

                conditional KeyType next()
                    {
                    if (Entry<KeyType, KeyType> entry : entryIterator)
                        {
                        return true, entry.key;
                        }
                    return false;
                    }
                };
            }

        @Override
        conditional EntryBasedKeySet<KeyType> remove(KeyType key)
            {
            Map newMap = Map.this.remove(key);
            assert Ref.equals(Map.this, newMap);
            return true, this;
            }

        @Override
        conditional EntryBasedKeySet<KeyType> removeIf(function Boolean (KeyType) shouldRemove)
            {
            Map newMap = Map.this.entries.removeIf(entry -> shouldRemove(entry.key));
            assert Ref.equals(Map.this, newMap);
            return true, this;
            }

        @Override
        conditional EntryBasedKeySet<KeyType> clear()
            {
            Map newMap = Map.this.clear();
            assert Ref.equals(Map.this, newMap);
            return true, this;
            }

        @Override
        Stream<KeyType> stream()
            {
            TODO
            }

        @Override
        EntryBasedKeySet<KeyType> clone()
            {
            return this;
            }
        }

    // ----- entries set implementations -----------------------------------------------------------

    /**
     * An implementation of the Set for the {@link Map.entries} property that delegates back to the
     * map and to the map's {@link Map.keys keys}.
     */
    class KeyBasedEntrySet<KeyType, ValueType>
            implements Set<Entry<KeyType, ValueType>>
        {
        @Override
        Int size.get()
            {
            return Map.this.size;
            }

        @Override
        Boolean empty.get()
            {
            return Map.this.empty;
            }

        @Override
        Iterator<Entry<KeyType, ValueType>> iterator()
            {
            return new Iterator()
                {
                Iterator keyIterator = Map.this.keys.iterator(); // TODO verify this is a private prop

                conditional KeyType next()
                    {
                    if (KeyType key : keyIterator)
                        {
                        // TODO verify this is private (a private property on the anon inner class)
                        static KeyBasedCursorEntry<KeyType, ValueType> entry = new KeyBasedCursorEntry(key);
                        return true, entry.advance(key);
                        }

                    return false;
                    }
                };
            }

        @Override
        conditional KeyBasedEntrySet<KeyType, ValueType> remove(Entry<KeyType, ValueType> entry)
            {
            // value is an Entry; remove the requested entry from the map only if the specified
            // entry's key/value pair exists in the map
            if (ValueType value : Map.this.get(entry.key))
                {
                if (value == entry.value)
                    {
                    Map newMap = Map.this.remove(entry.key);
                    assert Ref.equals(Map.this, newMap);
                    return true, this;
                    }
                }
            return false;
            }

        @Override
        conditional KeyBasedEntrySet<KeyType, ValueType> removeIf(
                function Boolean (Entry<KeyType, ValueType>) shouldRemove)
            {
            Set<KeyType> oldKeys = Map.this.keys;

            // temp fix, part 1 of 2:
//            static @Unassigned KeyBasedCursorEntry<KeyType, ValueType> entry;
            Set<KeyType> newKeys = oldKeys.removeIf(key ->
                {
                // REVIEW this line of code is possibly "so wrong" in so many ways:
                // 1) what does it mean to have a "static" variable inside of a lambda?
                //    a) does this mean that the state is owned by the lambda (making it stateFUL)
                //    b) .. or that the lambda is a method (captures "this") and thus can put a
                //       property onto the KeyBasedEntrySet itself?
                // 2) the compiler currently barfs on this because the identity of the lambda is not
                //    known during the validateExpressions() stage, so the property constant is
                //    considered to be "unresolved" (so MethodDeclarationStatement.resolveNames()
                //    requeues itself infinitely because method.resolveTypedefs() cannot succeed)
                //

                // this is the code that we actually want:
                // 1) "static" says that "hey, i need this to be 'here' for some definition of here,
                //    i.e. available every call into this lambda
                //    -> is it a property, which forces this lambda to be a method? (NOT ideal)
                //    -> or is it just a local variable that gets added to the frame that hosts this
                //       lambda, as if it were declared outside of this lambda and then captured?
                // 2) "lazy" says "just the first time, initialize it", which could conceivably be
                //    an implicit thing, written as:
                static @Lazy KeyBasedCursorEntry<KeyType, ValueType> entry.calc()
                    {
                    return new KeyBasedCursorEntry(key);
                    }

                // temp fix, part 2 of 2:
//                if (&entry.assigned)
//                    {
//                    entry = new KeyBasedCursorEntry(key);
//                    }

                return shouldRemove(entry.advance(key));
                });
            assert &newKeys == &oldKeys;

            return true, this;
            }

        @Override
        conditional KeyBasedEntrySet<KeyType, ValueType> clear()
            {
            Map newMap = Map.this.clear();
            assert Ref.equals(Map.this, newMap);
            return true, this;
            }

        @Override
        Stream<Entry<KeyType, ValueType>> stream()
            {
            TODO
            }

        @Override
        KeyBasedEntrySet<KeyType, ValueType> clone()
            {
            return this;
            }
        }

    // ----- Entry implementations -----------------------------------------------------------------

    /**
     * The primordial implementation of a simple Entry.
     */
    static class SimpleEntry<KeyType, ValueType>(KeyType key, ValueType value)
            implements Entry<KeyType, ValueType>
        {
        @Override
        public/private KeyType key;

        @Override
        ValueType value;
        }

    /**
     * An implementation of ProcessableEntry that delegates back to the map for a specified key.
     */
    class KeyBasedEntry<KeyType, ValueType>(KeyType key)
            implements ProcessableEntry<KeyType, ValueType>
        {
        @Override
        public/protected KeyType key;

        @Override
        Boolean exists.get()
            {
            return Map.this.get(key);
            }

        @Override
        ValueType value
            {
            @Override
            ValueType get()
                {
                if (ValueType value : Map.this.get(key))
                    {
                    return value;
                    }
                throw new BoundsException();
                }

            @Override
            void set(ValueType value)
                {
                Map.this.put(key, value);
                }
            }

        @Override
        void remove()
            {
            Map newMap = Map.this.remove(key);
            assert Ref.equals(Map.this, newMap);
            }
        }

    /**
     * An implementation of ProcessableEntry that can be used as a cursor over any number of keys,
     * and delegates back to the map for its functionality.
     */
    class KeyBasedCursorEntry<KeyType, ValueType>
            extends KeyBasedEntry<KeyType, ValueType>
        {
        construct(KeyType key)
            {
            construct KeyBasedEntry(key);
            }

        /**
         * Specify the new "cursor key" for this Entry.
         *
         * @param key  the new key for this Entry
         *
         * @return this Entry
         */
        KeyBasedCursorEntry<KeyType, ValueType> advance(KeyType key)
            {
            this.key = key;
            return this;
            }

        @Override
        ProcessableEntry<KeyType, ValueType> reify()
            {
            // this entry class is re-usable for different keys, so return an entry whose key cannot
            // be modified
            return new KeyBasedEntry<KeyType, ValueType>(key);
            }
        }

    // ----- values collection implementations -----------------------------------------------------

    /**
     * An implementation of the Collection for the {@link Map.values} property that delegates back
     * to the map and to the map's {@link Map.entries entries}.
     */
    class EntryBasedValuesCollection<ValueType>
            implements Collection<ValueType>
        {
        @Override
        Int size.get()
            {
            return Map.this.size;
            }

        @Override
        Boolean empty.get()
            {
            return Map.this.empty;
            }

        @Override
        Iterator<ValueType> iterator()
            {
            return new Iterator()
                {
                Iterator entryIterator = Map.this.entries.iterator();

                conditional ValueType next()
                    {
                    if (Entry<KeyType, ValueType> entry : entryIterator)
                        {
                        return true, entry.value;
                        }
                    return false;
                    }
                };
            }

        @Override
        conditional Collection<ValueType> remove(ValueType value)
            {
            Map.this.entries.iterator().untilAny(entry ->
                {
                if (entry.value == value)
                    {
                    Map newMap = Map.this.remove(entry.key);
                    assert Ref.equals(Map.this, newMap);
                    return true, this;
                    }
                return false;
                });

            return false;
            }

        @Override
        conditional Collection<ValueType> removeIf(function Boolean (ValueType) shouldRemove)
            {
            if (Collection<ValueType> newEntries :
                    Map.this.entries.removeIf(entry -> shouldRemove(entry.value)))
                {
                assert Ref.equals(Map.this, newMap);
                return true, this;
                }
            return false;
            }

        @Override
        conditional Collection<ValueType> clear()
            {
            if (Map.this.empty)
                {
                return false;
                }

            Map newMap = Map.this.clear();
            assert Ref.equals(Map.this, newMap);
            return true, this;
            }

        @Override
        Stream<ValueType> stream()
            {
            TODO
            }

        @Override
        EntryBasedValuesCollection<ValueType> clone()
            {
            return this;
            }
        }

    /**
     * An implementation of the Collection for the {@link Map.values} property that delegates back
     * to the map and to the map's {@link Map.keys keys}.
     */
    class KeyBasedValuesCollection<ValueType>
            implements Collection<ValueType>
        {
        @Override
        Int size.get()
            {
            return Map.this.size;
            }

        @Override
        Boolean empty.get()
            {
            return Map.this.empty;
            }

        @Override
        Iterator<ValueType> iterator()
            {
            return new Iterator()
                {
                Iterator keyIterator = Map.this.keys.iterator();

                conditional ValueType next()
                    {
                    if (KeyType key : keyIterator)
                        {
                        return Map.this.get(key);
                        }

                    return false;
                    }
                };
            }

        @Override
        conditional Collection<ValueType> remove(ValueType value)
            {
            Boolean modified = Map.this.keys.iterator().untilAny(key ->
                {
                if (ValueType keyvalue : Map.this.get(key) && keyvalue == value)
                    {
                    Map newMap = Map.this.remove(key);
                    assert Ref.equals(Map.this, newMap);
                    return true;
                    }
                return false;
                });

            return modified ? (true, this) : false;
            }

        @Override
        conditional Collection<ValueType> removeIf(function Boolean (ValueType) shouldRemove)
            {
            if (Collection<KeyType> newKeys : Map.this.keys.removeIf(key ->
                    {
                    assert ValueType value : Map.this.get(key);
                    return shouldRemove(value);
                    }))
                {
                assert Ref.equals(Map.this.keys, newKeys);
                return true, this;
                }

            return false;
            }

        @Override
        conditional Collection<ValueType> clear()
            {
            Map newMap = Map.this.clear();

            assert Ref.equals(Map.this, newMap);
            return true, this;
            }
        }
    }
