/**
 * A Map is a _dictionary_ data structure that stores _values_, each identified by a _key_; the
 * combination of a key and its value is an _entry_.
 *
 * The Map is one of the most commonly used data structures, because it allows information to be
 * easily _related to_ other information. The best known implementation is the [HashMap], which
 * uses [Hashable] key values.
 *
 * To implement the Map interface for a read-only Map, it is only necessary to implement the [keys]
 * property and the [get] method.
 *
 * To implement the Map interface for a read/write Map, it is additionally necessary to implement
 * the [put] and [remove] methods.
 */
interface Map<Key, Value>
        extends Stringable
    {
    /**
     * An Orderer is a function that compares two keys for order.
     */
    typedef Type<Key>.Orderer Orderer;


    // ----- metadata ------------------------------------------------------------------------------

    /**
     * Metadata: Are mutating operations on the map processed in place, or do they result in
     * a new copy of the map that incorporates any mutations? Any data structure that creates
     * a new copy to perform a mutation is called a _persistent_ data structure; that term is
     * generally avoided here because of the multiple meanings in software of the term "persistent".
     *
     * It is expected that all mutating operations that do not return a resulting map will
     * assert that `inPlace` is `True`.
     */
    @RO Boolean inPlace.get()
        {
        return True;
        }

    /**
     * Metadata: Is the Map maintained in a specific order? And if that order is a function
     * of the keys in the Map, what is the [Type.Orderer] that represents that ordering?
     *
     * @return True iff the Entry order within the Map is significant
     * @return (conditional) the Orderer that determines the order between two map keys; `Null`
     *         indicates that an order is maintained, but not by the comparison of keys, such as
     *         when a map stores entries in the order that they are added
     */
    conditional Orderer? ordered()
        {
        return False;
        }

    /**
     * Metadata: Is the Map of a known size? The size is available from the [size] property, but may
     * require significant effort to compute; this metadata (similar to that available on the
     * [Iterator] and [Collection] interfaces) provides an indication of whether the size is "free"
     * to obtain.
     *
     * @return True iff the `Map` size is efficiently known
     * @return (conditional) the `Map` size, if it is efficiently known
     */
    conditional Int knownSize()
        {
        // implementations of Map that do not have a cached size should override this method and
        // return False if the size requires any calculation more expensive than O(1)
        return True, size;
        }


    // ----- read operations -----------------------------------------------------------------------

    /**
     * Determine the size of the Map, which is the number of entries (key/value pairs) in the Map.
     */
    @RO Int size.get()
        {
        return keys.size;
        }

    /**
     * Determine if the Map is empty.
     *
     * This is equivalent to checking `size == 0`, but may be implemented more efficiently for Map
     * implementations that have a cost associated with calculating the size.
     */
    @RO Boolean empty.get()
        {
        return keys.empty;
        }

    /**
     * Check if this map contains the specified key.
     *
     * @param key  specifies the key that may or may not already be present in the map
     *
     * @return the True iff the specified key exists in the map
     */
    Boolean contains(Key key)
        {
        return keys.contains(key);
        }

    /**
     * Obtain the value associated with the specified key, iff that key is present in the map. If
     * the key is not present in the map, then this method returns a conditional `False`.
     *
     * @param key  the key to look up in the map
     *
     * @return a True iff the value associated with the specified key exists in the map
     * @return the value associated with the specified key (conditional)
     */
    conditional Value get(Key key);

    /**
     * Obtain the value associated with the specified key, or the value `Null` if the key is
     * not present in the map. This method supports the use of the `[]` operator:
     *
     *     value = map[key];
     *
     * @param key  the key to look up in the map
     *
     * @return the value for the associated key if it exists in the map; otherwise Null
     */
    @Op("[]") Value? getOrNull(Key key)
        {
        if (Value value := get(key))
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
    Value getOrDefault(Key key, Value dftval)
        {
        if (Value value := get(key))
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
    Value getOrCompute(Key key, function Value () compute)
        {
        if (Value value := get(key))
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
     * The returned set is expected to support mutation operations iff the map is `inPlace`; the
     * returned set is _not_ expected to support the `add` or `addAll` operations.
     */
    @RO Set<Key> keys;

    /**
     * Obtain the collection of all values (one for each key) in the map.
     *
     * The returned collection is expected to support mutation operations iff the map is `inPlace`;
     * the returned collection is _not_ expected to support the `add` or `addAll` operations.
     */
    @RO Collection<Value> values;

    /**
     * Obtain the collection of all entries (key/value pairs) in the map.
     *
     * The returned set is expected to support mutation operations iff the map is `inPlace`.
     */
    @RO Collection<Entry> entries;

    /**
     * Filter this `Map` based on its entries' keys and values.
     *
     * @param match  a function that evaluates a `Key` and `Value` for inclusion
     *
     * @return a Map containing entries indicated by the keys and values that matched the function
     */
    Map! filter(function Boolean(Entry) match)
        {
        return entries.filter(match).associate(entryAssociator());
        }

    /**
     * Create a sorted `Map` from this `Map`.
     *
     * @param order  (optional) [Type.Orderer] to control the sort order of the Map entries; `Null`
     *               means to use the Map's natural order, which is typically the natural order of
     *               the Map's keys
     *
     * @return a sorted Map
     *
     * @throws UnsupportedOperation  if no [Type.Orderer] is provided and [Key] is not [Orderable]
     */
    Map! sorted(Orderer? order = Null)
        {
        if (order == Null)
            {
            assert order := Key.ordered();
            }
        else if (Orderer? currentOrder := ordered(), order == currentOrder)
            {
            // optimization if the requested order is the same as the current order
            return this;
            }

        return sortedByEntry(orderByKey(order));
        }

    /**
     * Create a sorted `Map` from this `Map`.
     *
     * @param order  [Type.Orderer] to control the sort order of the Map entries
     *
     * @return a sorted Map
     */
    Map! sortedByEntry(function Ordered (Entry, Entry) order)
        {
        return entries.sorted(order).associate(entryAssociator());
        }

    /**
     * An implementation of a matching function that operators on entries, whose purpose is to match
     * on the values in those entries, by delegating to an underlying function that matches only on
     * the value.
     */
    function Boolean(Entry) valueMatches(function Boolean(Value) match)
        {
        return e -> match(e.value);
        }

    /**
     * A comparison function for entries that delegates to a comparison function on keys.
     */
    function Ordered (Entry, Entry) orderByKey(function Ordered (Key, Key) order)
        {
        return (e1, e2) -> order(e1.key, e2.key);
        }

    /**
     * An implementation of an associator, as used by [Collection.associate], that splits an [Entry]
     * into its constituent key and value.
     */
    function (Key, Value) (Entry) entryAssociator()
        {
        return e -> (e.key, e.value);
        }


    // ----- write operations ----------------------------------------------------------------------

    /**
     * Store a mapping of the specified key to the specified value, regardless of whether that key
     * is already present in the map.
     *
     * An `inPlace` map will perform the operation in place; otherwise the map will return a new map
     * reflecting the requested change.
     *
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key
     *
     * @return the resultant map, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
     Map put(Key key, Value value)
        {
        throw new ReadOnly("entry addition and modification is not supported");
        }

    /**
     * For an in-place Map, store a mapping of the specified key to the specified value, regardless
     * of whether that key is already present in the map. This method supports the use of the `[]`
     * operator:
     *
     *     map[key] = value;
     *
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
    @Op("[]=") void putInPlace(Key key, Value value)
        {
        if (inPlace)
            {
            put(key, value);
            }
        else
            {
            throw new ReadOnly("map does not support in-place modification");
            }
        }

    /**
     * Store in this map each of the mappings of key and values specified in another map, regardless
     * of whether those keys and/or values are already present in this map.
     *
     * An `inPlace` map will perform the operation in place; otherwise the map will return a new map
     * reflecting the requested change.
     *
     * @param that  another map containing keys and associated values to put into this map
     *
     * @return the resultant map, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
    Map putAll(Map! that)
        {
        Map result = this;
        for (Entry entry : that.entries)
            {
            result = result.put(entry.key, entry.value);
            }
        return result;
        }

    /**
     * Map the specified key to the specified value, iff that key is *not* currently present in the
     * map.
     *
     * An `inPlace` map will perform the operation in place; otherwise the map will return a new map
     * reflecting the requested change.
     *
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key iff the key does not already
     *               exist in the map
     *
     * @return True iff the key did not previously exist in the map and now it does
     * @return the resultant map, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
    conditional Map putIfAbsent(Key key, Value value)
        {
        if (keys.contains(key))
            {
            return False;
            }

        return True, put(key, value);
        }

    /**
     * Store the specified new value in the map associated with the specified key, iff that key is
     * currently associated with the specified old value.
     *
     * @param key       the key to store in the map
     * @param valueOld  the value to verify is currently associated with the specified key
     * @param valueNew  the value to associate with the specified key
     *
     * @return True iff the key did exist in the map and was associated with `valueOld`
     * @return the resultant map, which is the same as `this` for an in-place map
     */
    conditional Map replace(Key key, Value valueOld, Value valueNew)
        {
        if (valueOld != valueNew)
            {
            if (Value valueCur := get(key), valueOld == valueCur)
                {
                return True, put(key, valueNew);
                }
            }

        return False;
        }

    /**
     * Remove the specified key and any associated value from this map.
     *
     * @param key  the key to remove from this map
     *
     * @return the resultant map, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
    Map remove(Key key)
        {
        throw new ReadOnly("entry removal is not supported");
        }

    /**
     * Remove the specified key and the associated value from this map, iff the key exists in this
     * map and is associated with the specified value.
     *
     * @param key    the key to remove from the map
     * @param value  the value to verify is currently associated with the specified key
     *
     * @return True iff the key did exist in the map and was associated with `value`
     * @return the resultant map, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
    conditional Map remove(Key key, Value value)
        {
        if (Value valueOld := get(key), value == valueOld)
            {
            return True, remove(key);
            }

        return False;
        }

    /**
     * Remove all key/value mappings from the map.
     *
     * An `inPlace` map will perform the operation in place; otherwise the map will return a new map
     * reflecting the requested change.
     *
     * @return the resultant map, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
    Map clear()
        {
        // this method should be overridden by any class that has a more efficient implementation
        // available
        Map result = this;
        if (inPlace)
            {
            keys.clear();
            }
        else
            {
            for (Key key : keys)
                {
                result = result.remove(key);
                }
            }
        return result;
        }


    // ----- Entry operations -----------------------------------------------------------

    /**
     * Apply the specified function to the entry for the specified key. If that key does not exist
     * in the map, then an `Entry` is provided to it whose [Entry.exists] property is `False`;
     * setting the value of the entry will cause the entry to _appear in_  (be added to) the map,
     * which is to say that the map will now contain an entry for that key with that value.
     * Similarly, calling [Entry.delete] on the entry will ensure that the key and any associated
     * value are now _not_ present in the map.
     *
     * @param key      specifies the key of the entry to process
     * @param compute  the function that will operate against the Entry
     *
     * @return the result of the specified function
     *
     * @throws ReadOnly  if the map does not support in-place mutation and the `compute` function
     *                   attempts to modify an entry
     */
    <Result> Result process(Key                    key,
                            function Result(Entry) compute)
        {
        Entry  entry  = new @maps.KeyEntry(key) Entry() {};
        Result result = compute(entry);
        return result;
        }

    /**
     * Apply the specified function to the Entry objects for the specified keys.
     *
     * @param keys     specifies which keys to process
     * @param compute  the function that will operate against each Entry
     *
     * @return a Map containing the results from the specified function applied against the entries
     *         for the specified keys
     *
     * @throws ReadOnly  if the map does not support in-place mutation and the `compute` function
     *                   attempts to modify an entry
     */
    <Result> Map!<Key, Result> processAll(Iterable<Key>          keys,
                                          function Result(Entry) compute)
        {
        ListMap<Key, Result> result = new ListMap(keys.size);
        for (Key key : keys)
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
     * @throws ReadOnly  if the map does not support in-place mutation and the `compute` function
     *                   attempts to modify an entry
     */
    <Result> conditional Result processIfPresent(Key                    key,
                                                 function Result(Entry) compute)
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
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
    Value computeIfAbsent(Key              key,
                          function Value() compute)
        {
        return process(key, entry ->
            {
            if (entry.exists)
                {
                return entry.value;
                }

            if (!inPlace)
                {
                throw new ReadOnly();
                }

            Value value = compute();
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
     * must call [reify] on each entry that will be held beyond the scope of the current iteration.
     *
     * A consumer can determine whether an entry exists (is present in a map), to cause an entry to
     * be created (if it did not exist) by assigning a value, to change the value of an entry that
     * does exist, and to remove the entry if it exists.
     */
    interface Entry
        {
        /**
         * The key represented by the entry.
         */
        @RO Key key;

        /**
         * True iff the entry is existent in its map. An entry does not exist in its map before its
         * [value] has been assigned, or after a call to [remove].
         */
        @RO Boolean exists;

        /**
         * The value associated with the entry's key.
         *
         * The value property is not settable and the entry is not removable if the containing map
         * is not `inPlace`.
         *
         * If the entry does not [exist](exists), then the value is not readable; an attempt to get
         * the value of an will raise an `OutOfBounds`
         *
         * @throws OutOfBounds  if an attempt is made to read the value of the entry when {@link
         *                      exists} is False
         * @throws ReadOnly     if an attempt is made to write the value of the entry and the map
         *                      is not `inPlace`, or does not support mutation
         */
        Value value;

        /**
         * Remove the entry from its map.
         *
         * The entry is not removable if the containing map is not `inPlace`.
         *
         * @throws ReadOnly  if an attempt is made to write the value of the entry and the map
         *                   is not `inPlace`, or does not support mutation
         */
        void delete()
            {
            throw new ReadOnly();
            }

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

        @Override
        String toString()
            {
            Key   key   = this.key;
            Value value = this.value;
            if (key.is(Stringable) && value.is(Stringable))
                {
                val buf = new StringBuffer(key.estimateStringLength() + 1
                                       + value.estimateStringLength());
                key.appendTo(buf).add(' ');
                return value.appendTo(buf).toString();
                }

            return key.toString() + ' ' + value.toString();
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


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        if (Key.is(Type<Stringable>) && Value.is(Type<Stringable>))
            {
            Int total = 3 * size;
            for ((Key key, Value value) : this)
                {
                total += key.estimateStringLength() + value.estimateStringLength();
                }
            return total;
            }

        // no inexpensive way to guess
        return 0;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        buf.add('[');

        if (Key.is(Type<Stringable>) && Value.is(Type<Stringable>))
            {
            Loop: for ((Key key, Value value) : this)
                {
                if (!Loop.first)
                    {
                    ", ".appendTo(buf);
                    }
                key.appendTo(buf);
                buf.add('=');
                value.appendTo(buf);
                }
            }
        else
            {
            Loop: for ((Key key, Value value) : this)
                {
                if (!Loop.first)
                    {
                    ", ".appendTo(buf);
                    }

                if (key.is(Stringable))
                    {
                    key.appendTo(buf);
                    }
                else
                    {
                    key.toString().appendTo(buf);
                    }

                buf.add('=');

                if (value.is(Stringable))
                    {
                    value.appendTo(buf);
                    }
                else
                    {
                    value.toString().appendTo(buf);
                    }
                }
            }

        return buf.add(']');
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

        for ((CompileType.Key key1, CompileType.Value value1) : map1)
            {
            if (CompileType.Value value2 := map2.get(key1), value2 == value1)
                {
                continue;
                }
            return False;
            }

        return True;
        }
    }
