/**
 * A `Map` is a _dictionary_ data structure that stores _values_, each identified by a _key_; the
 * combination of a key and its value is an [Entry].
 *
 * The `Map` is one of the most commonly used data structures, because it allows information to be
 * easily _related to_ other information. The best known implementation is the [HashMap], which
 * uses [Hashable] key values. Literal `Map` values in Ecstasy are represented using [ListMap],
 * which maintains the entries in the order described (or the order they were added).
 */
interface Map<Key, Value>
        extends MapAppender<Key, Value>
        extends Stringable {
    /**
     * An Orderer is a function that compares two keys for order.
     */
    typedef Type<Key>.Orderer as Orderer;

    // ----- metadata ------------------------------------------------------------------------------

    /**
     * Metadata: Are mutating operations on the `Map` processed in place, or do they result in
     * a new copy of the `Map` that incorporates any mutations? Any data structure that creates
     * a new copy to perform a mutation is called a _persistent_ data structure; that term is
     * generally avoided here because of the multiple meanings in software of the term "persistent".
     *
     * It is expected that all mutating operations that do not return a resulting `Map` will
     * assert that `inPlace` is `True`.
     */
    @RO Boolean inPlace.get() {
        return True;
    }

    /**
     * Metadata: Is the `Map` maintained in a specific order? And if that order is a function
     * of the keys in the `Map`, what is the [Type.Orderer] that represents that ordering?
     *
     * @return True iff the [Entry] order within the `Map` is significant
     * @return (conditional) the [Orderer] that determines the order between two `Map` keys; `Null`
     *         indicates that an order is maintained, but not by the comparison of keys, such as
     *         when a `Map` stores entries in the order that they are added, or if the order is
     *         based on the `Entry` and not just the `Key`
     */
    conditional Orderer? ordered() {
        return False;
    }

    /**
     * Metadata: Is the `Map` of a known size? The size is available from the [size] property, but
     * may require significant effort to compute; this metadata (similar to that available on the
     * [Iterator] and [Collection] interfaces) provides an indication of whether the size is "free"
     * to obtain.
     *
     * @return True iff the `Map` size is efficiently known
     * @return (conditional) the `Map` size
     */
    @Concurrent
    conditional Int knownSize() {
        // implementations of Map that do not have a cached size should override this method and
        // return False if the size requires any calculation more expensive than O(1)
        return True, size;
    }

    // ----- read operations -----------------------------------------------------------------------

    /**
     * The size of the `Map`, which is the number of [Entry] objects (key/value pairs) in the `Map`.
     */
    @Concurrent
    @RO Int size;

    /**
     * True iff the `Map` is empty.
     *
     * This is equivalent to computing `size == 0`, but _may_ be implemented more efficiently for
     * `Map` implementations that have a cost associated with calculating the size.
     */
    @Concurrent
    @RO Boolean empty.get() = size == 0;

    /**
     * Check if this `Map` contains the specified key.
     *
     * @param key  specifies the key that may or may not already be present in the map
     *
     * @return the True iff the specified key exists in the map
     */
    @Concurrent
    Boolean contains(Key key) = get(key);

    /**
     * Obtain the value associated with the specified key, iff that key is present in the `Map`. If
     * the key is not present in the `Map`, then this method returns a conditional `False`.
     *
     * @param key  the key to look up in the map
     *
     * @return a True iff the value associated with the specified key exists in the map
     * @return the value associated with the specified key (conditional)
     */
    conditional Value get(Key key);

    /**
     * Obtain the value associated with the specified key, or the value `Null` if the key is
     * not present in the `Map`. This method supports the use of the `[]` operator:
     *
     *     value = map[key];
     *
     * @param key  the key to look up in the map
     *
     * @return the value for the associated key if it exists in the map; otherwise Null
     */
    @Concurrent
    @Op("[]") Value? getOrNull(Key key) {
        if (Value value := get(key)) {
            return value;
        }

        return Null;
    }

    /**
     * Obtain the value associated with the specified key, or if the key does not already exist in
     * the `Map`, return the specified default value. Note that his method does not store the default
     * if the key is missing; it simply returns it. To store the result, use [computeIfAbsent]
     * instead.
     *
     * @param key     specifies the key that may or may not already be present in the map
     * @param dftval  the default value
     *
     * @return the value for the specified key if it exists in the map; otherwise, the default value
     */
    @Concurrent
    Value getOrDefault(Key key, Value dftval) {
        if (Value value := get(key)) {
            return value;
        }

        return dftval;
    }

    /**
     * Obtain the value associated with the specified key, or if the key does not already exist in
     * the `Map`, compute a default value using the specified function. Note that his method does
     * **not** store the result of the computation; it simply returns the computed value. To store
     * the result, use [computeIfAbsent] instead.
     *
     * @param key      specifies the key that may or may not already be present in the map
     * @param compute  the function that will be called iff the key does not exist in the map
     *
     * @return the value associated with the specified key iff the key exists in the map; otherwise,
     *         the result from the provided function
     */
    @Concurrent
    Value getOrCompute(Key key, function Value () compute) {
        if (Value value := get(key)) {
            return value;
        }

        return compute();
    }

    /**
     * Obtain an [Iterator] through the `Map`'s entries. The returned `Iterator` is permitted to
     * re-use the same [Entry] instance to represent in turn each entry in the entire `Map`, thus
     * avoiding repeated allocations.
     *
     * If a caller wants to retain any `Entry` instance returned from this `Iterator` beyond the
     * next call to [Iterator.next]), then the caller **must** call [Entry.reify()] before making a
     * call to [Iterator.next()].
     *
     * @return an `Iterator` of `Entry` that is permitted to return unreified `Entry` instances
     */
    Iterator<Entry<Key, Value>> iterator();

    /**
     * Obtain the set of all keys in the `Map`.
     *
     * Example usage:
     *
     *   if (map.keys.contains(name)) {...}
     *
     * The returned set is expected to support mutation operations iff the `Map` is [inPlace]; the
     * returned set is _not_ expected to support the `add` or `addAll` operations.
     */
    @RO Set<Key> keys;

    /**
     * Obtain the collection of all values (one for each key) in the `Map`.
     *
     * The returned collection is expected to support mutation operations iff the `Map` is
     * [inPlace]; the returned [Collection] is _not_ expected to support the `add` or `addAll`
     * operations.
     */
    @RO Collection<Value> values;

    /**
     * Obtain the collection of all entries (key/value pairs) in the `Map`.
     *
     * The returned set is expected to support mutation operations iff the `Map` is [inPlace].
     */
    @RO Collection<Entry<Key, Value>> entries;

    /**
     * Filter this `Map` based on its [Entry] objects.
     *
     * The `Map` returned from this call may depend on (be a _view of_) this `Map`, which means that
     * changes to this `Map` may alter the apparent contents of the returned `Map`; to ensure that
     * changes to this `Map` do **not** alter the apparent contents of the returned `Map`, use the
     * [reify] method on the returned `Map`. Furthermore, if this is an [inPlace] `Map` and the
     * the result of this method is an unreified view of this `Map`, then attempts to call [put],
     * [remove], or any other in-place mutating operations on the returned view `Map` will throw a
     * [ReadOnly] exception, because the view result is forbidden to modify the original `Map` on
     * which it is based; again, the solution is to explicitly invoke the [reify] method on the
     * returned `Map` before attempting to do any in-place mutation on the returned `Map`.
     *
     * @param match  a function that evaluates an [Entry] for inclusion
     *
     * @return a `Map` containing only the [Entry] objects indicated by the provided function
     */
    @Concurrent
    Map! filter(function Boolean(Entry<Key, Value>) match) {
        import deferred.FilteredMap;
        return new FilteredMap<Key, Value>(this, match);
    }

    /**
     * Evaluate the contents of this `Map` using the provided criteria, and produce a resulting
     * `Map` that contains only the [Entry] objects that match.
     *
     * @param match      a function that evaluates an [Entry] of the `Map` for inclusion
     * @param collector  a [MapCollector] to use to collect the results
     *
     * @return the resulting `Map` containing the entries that matched the provided criteria,
     *         potentially further "reduced" by the specified collector
     */
    @Concurrent
    <Result extends Map!> Result filter(function Boolean(Entry<Key, Value>) match,
                                        MapCollector<Key, Value, Result>    collector) {
        var accumulator = collector.init();
        if (!empty) {
            for (val entry : iterator()) {
                if (match(entry)) {
                    accumulator = accumulator.put(entry.key, entry.value);
                }
            }
        }
        return collector.reduce(accumulator);
    }

    /**
     * Partition the [Entry] objects of the `Map` into those that match the provided criteria, and
     * those that do not.
     *
     * The `Map` returned from this call may depend on (be a _view of_) this `Map`, which means that
     * changes to this `Map` may alter the apparent contents of the returned `Map`; to ensure that
     * changes to this `Map` do **not** alter the apparent contents of the returned `Map`, use the
     * [reify] method on the returned `Map`. Furthermore, if this is an [inPlace] `Map` and the
     * the result of this method is an unreified view of this `Map`, then attempts to call [put],
     * [remove], or any other in-place mutating operations on the returned view `Map` will throw a
     * [ReadOnly] exception, because the view result is forbidden to modify the original `Map` on
     * which it is based; again, the solution is to explicitly invoke the [reify] method on the
     * returned `Map` before attempting to do any in-place mutation on the returned `Map`.
     *
     * @param match      a function that evaluates an [Entry] of the `Map` for inclusion
     *
     * @return matches   a `Map` of [Entry] objects that match the provided criteria
     * @return misses    the list of [Entry] objects that **do not** match the provided criteria
     */
    @Concurrent
    (Map! matches, Map! misses) partition(function Boolean(Entry<Key, Value>) match) {
        import deferred.PartitionedMap;
        PartitionedMap<Key, Value> matches = new PartitionedMap(this, match);
        return matches, matches.inverse;
    }

    /**
     * Partition the [Entry] objects of the `Map` into those that match the provided criteria, and
     * those that do not.
     *
     * @param match      a function that evaluates an [Entry] of the `Map` for inclusion
     * @param collector  a [MapCollector] to use to collect the results
     *
     * @return matches   the `Map` of elements that match the provided criteria and further
     *                   "reduced" by the specified collector
     * @return misses    the `Map` of elements that **do not** match the provided criteria
     *                   and further "reduced" by the specified collector
     */
    @Concurrent
    <Result extends Map!> (Result matches, Result misses) partition(
            function Boolean(Entry<Key, Value>) match,
            MapCollector<Key, Value, Result>    collector) {
        var matches = collector.init();
        var misses  = collector.init();
        if (&matches == &this) {
            for (val entry : iterator()) {
                if (!match(entry)) {
                    misses = misses.put(entry.key, entry.value);
                    entry.delete();
                }
            }
        } else if (&misses == &this) {
            for (val entry : iterator()) {
                if (match(entry)) {
                    matches = matches.put(entry.key, entry.value);
                    entry.delete();
                }
            }
        } else {
            for (val entry : iterator()) {
                if (match(entry)) {
                    matches = matches.put(entry.key, entry.value);
                } else {
                    misses = misses.put(entry.key, entry.value);
                }
            }
        }
        return collector.reduce(matches), collector.reduce(misses);
    }

    /**
     * Build a `Map` that has one value "mapped from" each [Entry] in this `Map`, using the provided
     * function.
     *
     * The `Map` returned from this call may depend on (be a _view of_) this `Map`, which means that
     * changes to this `Map` may alter the apparent contents of the returned `Map`; to ensure that
     * changes to this `Map` do **not** alter the apparent contents of the returned `Map`, use the
     * [reify] method on the returned `Map`. Furthermore, if this is an [inPlace] `Map` and the
     * the result of this method is an unreified view of this `Map`, then attempts to call [put],
     * [remove], or any other in-place mutating operations on the returned view `Map` will throw a
     * [ReadOnly] exception, because the view result is forbidden to modify the original `Map` on
     * which it is based; again, the solution is to explicitly invoke the [reify] method on the
     * returned `Map` before attempting to do any in-place mutation on the returned `Map`.
     *
     * @param transform  a function that creates a "mapped" value from each [Entry] in this `Map`
     *
     * @return the resulting `Map` containing values "mapped from" values in this `Map`
     */
    <ToValue> Map!<Key, ToValue> map(function ToValue(Entry<Key, Value>) transform) {
        if (Int count := knownSize(), count == 0) {
            return [];
        }
        import deferred.MappedMap;
        return new MappedMap<Key, ToValue, Value>(this, transform);
    }

    /**
     * Build a `Map` that has one value "mapped from" each [Entry] in this `Map`, using the provided
     * function.
     *
     * @param transform  a function that creates a "mapped" value from each [Entry] in this `Map`
     * @param collector  an [MapCollector] to use to collect the results
     *
     * @return the resulting `Map` containing values "mapped from" values in this `Map`, and
     *         potentially further "reduced" by the specified collector
     */
    <ToValue, Result extends Map!<Key, ToValue>> Result map(
            function ToValue(Entry<Key, Value>) transform,
            MapCollector<Key, ToValue, Result>  collector) {
        var accumulator = collector.init(knownSize() ?: 0);
        if (&accumulator == &this) {
            assert inPlace;
            assert ToValue.is(Type<Value>);
            for (val entry : iterator()) {
                entry.value = transform(entry);
            }
        } else {
            for (val entry : iterator()) {
                accumulator = accumulator.put(entry.key, transform(entry));
            }
        }
        return collector.reduce(accumulator);
    }

    /**
     * Build a new `Map` that has one key/value pair "mapped from" each [Entry] in this `Map`, using
     * the provided function. This flavor of the `map()` function is used when the resulting `Map`
     * has a different `Key` type from the original `Map`.
     *
     * @param transform  a function that creates a "mapped" key/value pair from each [Entry] in this
     *                   `Map`
     * @param collector  an [MapCollector] to use to collect the results
     *
     * @return the resulting `Map` containing values "mapped from" values in this `Map`, and
     *         potentially further "reduced" by the specified collector
     */
    <ToKey, ToValue, Result extends Map!<ToKey, ToValue>> Result map(
            function (ToKey, ToValue)(Entry<Key, Value>) transform,
            MapCollector<ToKey, ToValue, Result>         collector) {
        var accumulator = collector.init(knownSize() ?: 0);
        for (val entry : iterator()) {
            (ToKey key, ToValue value) = transform(entry);
            accumulator = accumulator.put(key, value);
        }
        return collector.reduce(accumulator);
    }

    /**
     * Reduce this `Map` of key/value pairs to a result value using the provided function. This
     * operation is also called a _folding_ operation. The passed function must be aware of the
     * possibility that each `Entry` passed to it could be unreified, based on the contract of
     * the [iterator()] method.
     *
     * @param initial     the initial value to start accumulating from
     * @param accumulate  the function that will be used to accumulate key/value pairs into a result
     *
     * @return the result of the reduction
     */
    @Concurrent
    <Result> Result reduce(Result                                     initial,
                           function Result(Result, Entry<Key, Value>) accumulate) {
        Result result = initial;
        for (val entry : iterator()) {
            result = accumulate(result, entry);
        }
        return result;
    }

    /**
     * Create a sorted `Map` from this `Map`, sorting the contents of the `Map` by its keys.
     *
     * @param order  (optional) the [Type.Orderer] to sort the keys of the `Map` by; `Null` means to
     *               use the `Key` type's natural order
     *
     * @return a sorted Map
     *
     * @throws Unsupported  if no [Type.Orderer] is provided and the [Key] type is not [Orderable]
     */
    @Concurrent
    Map! sorted(Orderer? order = Null) {
        if (order == Null) {
            assert order := Key.ordered();
        }

        if (Orderer? currentOrder := ordered(), order == currentOrder) {
            // optimization if the requested order is the same as the current order
            return this;
        }
        assert Key.is(Type<Orderable>);
        return new SkiplistMap<Key, Value>(size, order).putAll(this);
    }

    /**
     * Create a sorted `Map` from this `Map`.
     *
     * @param order      [Type.Orderer] to control the sort order of the `Map` entries
     * @param collector  an optional [MapCollector] to use to collect the sorted results
     *
     * @return a sorted copy of this `Map`, ordered (and optionally collected) as specified
     */
    @Concurrent
    <Result extends Map!<Key, Value>> Result sortedByEntry(
            function Ordered(Entry<Key, Value>, Entry<Key, Value>) order,
            MapCollector<Key, Value, Result>?                      collector = Null) {
        val inOrder = entries.sorted(order);
        Int count   = inOrder.size;
        if (collector == Null) {
            Key[]   keys = new Key  [count](i -> inOrder[i].key);
            Value[] vals = new Value[count](i -> inOrder[i].value);
            return new ListMap<Key, Value>(keys, vals).as(Result);
        } else {
            var accumulator = collector.init();
            for (Int i = 0; i < count; ++i) {
                val entry = inOrder[i];
                accumulator = accumulator.put(entry.key, entry.value);
            }
            return collector.reduce(accumulator);
        }
    }

    /**
     * An implementation of a matching function that operators on entries, whose purpose is to match
     * on the values in those entries, by delegating to an underlying function that matches only on
     * the value.
     */
    function Boolean(Entry<Key, Value>) valueMatches(function Boolean(Value) match) {
        return e -> match(e.value);
    }

    /**
     * A comparison function for entries that delegates to a comparison function on keys.
     */
    function Ordered(Entry<Key, Value>, Entry<Key, Value>) orderByKey(function Ordered(Key, Key) order) {
        return (e1, e2) -> order(e1.key, e2.key);
    }

    /**
     * An implementation of an associator, as used by [Collection.associate], that splits an [Entry]
     * into its constituent key and value.
     */
    function (Key, Value) (Entry<Key, Value>) entryAssociator() {
        return e -> (e.key, e.value);
    }

    /**
     * Obtain a [MapCollector] that produces a `Map` that is substantially similar to what a typical
     * developer would expect this `Map` to produce. A [HashMap], for example, would be expected to
     * produce a `HashMap`, and a [SkiplistMap] would be expected to produce a `SkiplistMap`.
     * Specifically, it is primarily important that a `Map` provide a `MapCollector` that produces a
     * `Map` whose [inPlace] value matches that of the original `Map`, and secondarily that the
     * result of the [ordered] method matches that of the original `Map`.
     *
     * @return the default [MapCollector] to use with this `Map`
     */
    MapCollector<Key, Value> defaultCollector() {
        // Maps that implement Replicable can be automatically supported via their replicator
        // constructor
        if ((Replicable + Map<Key, Value>) map := this.is(Replicable)) {
            return new MapCollector<Key, Value>(() -> map.new());
        }

        // Map implementations that care about producing a specific class of Map, or otherwise wish
        // to tailor the resulting MapConverter should override this method
        if (inPlace, Key.is(Type<Orderable>), Orderer? orderer := ordered(), orderer != Null) {
            // SkiplistMap is an inPlace + ordered Map implementation
            return new MapCollector<Key, Value>(
                    () ->  new SkiplistMap<Key, Value>(orderer=orderer));
        }
        // ListMap will keep the order of insertion, and supports both inPlace and not inPlace
        return new ListMapCollector<Key, Value>(!this.inPlace);
    }

    /**
     * Obtain a `Map` that has the same contents as this `Map`, but which has two additional
     * attributes:
     *
     * * First, if this `Map` is dependent on another `Map` for its storage such that the other
     *   `Map` may contain additional data that is not present in this `Map`, then the resulting
     *   `Map` will no longer be dependent on that other `Map` for its storage (i.e. it will hold
     *   its own copy of its data, and release its reference to the other `Map`, which may allow
     *   memory to be reclaimed);
     *
     * * Second, if this `Map` is dependent on another `Map` such that changes to this `Map` may be
     *   visible in the other `Map`, and/or that changes to the other `Map` may be visible in this
     *   `Map`, then the resulting `Map` will no longer have that attribute, i.e. changes to the
     *   resulting `Map` will not be visible in the other `Map`, nor will changes to the other `Map`
     *   be visible in the resulting `Map`. This guarantee is essential when a `Map` may have
     *   resulted from an operation (such as the [map] method) that may defer most of the work of
     *   the operation by holding onto the original `Map` -- which may itself be subject to later
     *   mutation -- and when the result itself is held long enough that subsequent mutation of the
     *   original `Map` may occur.
     *
     * This contract is designed to allow `Map` implementations to take advantage of lazy and
     * deferred behavior in order to achieve time and space optimizations.
     *
     * If this method is invoked with a [MapCollector] specified, then the contents of this `Map`
     * will be reified using that `MapCollector`, whether or not this `Map` is already reified.
     *
     * @param collector  (optional) a [MapCollector] to use for reification
     *
     * @return a reified `Map`, which will be `this` if this `Map` is already reified and no
     *         [MapCollector] is specified
     */
    @Concurrent
    <Result extends Map!> Result reify(MapCollector<Key, Value, Result>? collector = Null) {
        if (collector != Null) {
            var accumulator = collector.init();
            accumulator = accumulator.putAll(this);
            return collector.reduce(accumulator);
        }

        // this method must be overridden by any implementing Map that may return a view of itself
        // as a Map, such that mutations to one might be visible from the other
        return this.as(Result);
    }

    // ----- write operations ----------------------------------------------------------------------

    /**
     * Store a mapping of the specified key to the specified value, regardless of whether that key
     * is already present in the `Map`.
     *
     * An [inPlace] `Map` will perform the operation in place; otherwise the `Map` will return a new
     * `Map` reflecting the requested change.
     *
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key
     *
     * @return the resultant `Map`, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the `Map` does not allow or support the requested mutating operation
     */
    @Override
    Map put(Key key, Value value) {
        throw new ReadOnly("entry addition and modification is not supported");
    }

    /**
     * For an in-place `Map`, store a mapping of the specified key to the specified value, regardless
     * of whether that key is already present in the `Map`. This method supports the use of the `[]`
     * operator:
     *
     *     map[key] = value;
     *
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key
     *
     * @throws ReadOnly  if the `Map` does not allow or support the requested mutating operation
     */
    @Concurrent
    @Op("[]=") void putInPlace(Key key, Value value) {
        if (inPlace) {
            put(key, value);
        } else {
            throw new ReadOnly("map does not support in-place modification");
        }
    }

    /**
     * Store in this `map` each of the mappings of key and values specified in another `Map`,
     * regardless of whether those keys and/or values are already present in this `Map`.
     *
     * An [inPlace] `Map` will perform the operation in place; otherwise the `Map` will return a new
     * `Map` reflecting the requested change.
     *
     * @param that  another `Map` containing keys and associated values to put into this map
     *
     * @return the resultant `Map`, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the `Map` does not allow or support the requested mutating operation
     */
    @Concurrent
    @Override
    Map putAll(Map! that) {
        Map result = this;
        for ((Key key, Value value) : that) {
            result = result.put(key, value);
        }
        return result;
    }

    /**
     * Map the specified key to the specified value, iff that key is *not* currently present in the
     * `Map`.
     *
     * An [inPlace] `Map` will perform the operation in place; otherwise the `Map` will return a new
     * `Map` reflecting the requested change.
     *
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key iff the key does not already
     *               exist in the map
     *
     * @return True iff the key did not previously exist in the `Map` and now it does
     * @return the resultant `Map`, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the `Map` does not allow or support the requested mutating operation
     */
    @Concurrent
    conditional Map putIfAbsent(Key key, Value value) {
        if (contains(key)) {
            return False;
        }

        return True, put(key, value);
    }

    /**
     * Store the specified new value in the `Map` associated with the specified key, iff that key is
     * currently associated with the specified old value.
     *
     * @param key       the key to store in the map
     * @param valueOld  the value to verify is currently associated with the specified key
     * @param valueNew  the value to associate with the specified key
     *
     * @return `True` iff the key did exist in the `Map` and was associated with `valueOld`
     * @return the resultant `Map`, which is the same as `this` for an in-place map
     */
    @Concurrent
    conditional Map replace(Key key, Value valueOld, Value valueNew) {
        if (Value valueCur := get(key), valueOld == valueCur) {
            return True, put(key, valueNew);
        }

        return False;
    }

    /**
     * Remove the specified key and any associated value from this `Map`.
     *
     * @param key  the key to remove from this map
     *
     * @return the resultant `Map`, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the `Map` does not allow or support the requested mutating operation
     */
    Map remove(Key key) {
        throw new ReadOnly("entry removal is not supported");
    }

    /**
     * Remove the specified key and the associated value from this `Map`, iff the key exists in this
     * `Map` and is associated with the specified value.
     *
     * @param key    the key to remove from the map
     * @param value  the value to verify is currently associated with the specified key
     *
     * @return True iff the key did exist in the `Map` and was associated with `value`
     * @return the resultant `Map`, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the `Map` does not allow or support the requested mutating operation
     */
    @Concurrent
    conditional Map remove(Key key, Value value) {
        if (Value valueOld := get(key), value == valueOld) {
            return True, remove(key);
        }

        return False;
    }

    /**
     * Remove all of the specified keys from this `Map`.
     *
     * If this `Map` is [inPlace], then the mutation occurs to this `Map`; otherwise, a new `Map`
     * with the mutation applied is returned.
     *
     * @param keys  an iterable source providing keys to remove from this `Map`
     *
     * @return the resulting `Map`, which is always `this` for an in-place `Map`
     *
     * @throws ReadOnly  if the `Map` does not support element removal
     */
    @Concurrent
    @Op("-") Map removeAll(Iterable<Key> keys) {
        Map result = this;
        for (Key key : keys) {
            result = result.remove(key);
        }
        return result;
    }

    /**
     * For each [Entry] in the `Map`, evaluate it using the specified function, removing each
     * `Entry` for which the specified function evaluates to `True`.
     *
     * If this `Map` is [inPlace], then the mutation occurs to this `Map`; otherwise, a new `Map`
     * with the mutation applied is returned.
     *
     * @param shouldRemove  a function used to filter this `Map`, returning `False` for each [Entry]
     *                      of this `Map` that should be removed
     *
     * @return the resulting `Map`, which is always `this` for an in-place map
     * @return the number of elements removed
     *
     * @throws ReadOnly  if the `Map` does not support element removal
     */
    @Concurrent
    (Map, Int) removeAll(function Boolean(Entry<Key, Value>) shouldRemove) {
        if (inPlace) {
            Int count = 0;
            for (val entry : iterator()) {
                if (shouldRemove(entry)) {
                    entry.delete();
                    ++count;
                }
            }
            return this, count;
        } else {
            Key[] deleteKeys = new Key[];
            for (val entry : iterator()) {
                if (shouldRemove(entry)) {
                    deleteKeys += entry.key;
                }
            }
            return removeAll(deleteKeys), deleteKeys.size;
        }
    }

    /**
     * Remove all key/value mappings from the `Map`.
     *
     * An [inPlace] `Map` will perform the operation in place; otherwise the `Map` will return a new
     * `Map` reflecting the requested change.
     *
     * @return the resultant `Map`, which is the same as `this` for an in-place map
     *
     * @throws ReadOnly  if the `Map` does not allow or support the requested mutating operation
     */
    @Concurrent
    Map clear() {
        // this method should be overridden by any class that has a more efficient implementation
        // available
        if (empty) {
            return this;
        }

        Map result = this;
        if (inPlace) {
            for (val entry : iterator()) {
                entry.delete();
            }
        } else {
            for (val entry : iterator()) {
                result = result.remove(entry.key);
            }
        }
        return result;
    }

    // ----- Entry interface -----------------------------------------------------------------------

    /**
     * An `Entry` represents a "key/value pair", which is single mapping from a particular `Key` to
     * its `Value`.
     *
     * The recipient of an `Entry` can determine whether the `Entry` [exists] (is present in the
     * `Map`); to cause the `Entry` to be created (if it did not exist in the `Map`) by setting its
     * [value]; to modify the [value] of an existing `Entry`; or to [delete] the `Entry` from the
     * `Map`.
     *
     * The `Entry` interface is designed to allow a `Map` implementor to re-use a temporary `Entry`
     * instance to represent a _current_ `Entry`, for example during iteration; this allows a single
     * `Entry` object to represent every `Entry` in the `Map`. This approach is intended to reduce
     * wasteful temporary allocations.
     */
    static interface Entry<Key, Value> {
        /**
         * The key represented by the `Entry`.
         */
        @RO Key key;

        /**
         * `True` iff the `Entry` is existent in its [Map]. AN `Entry` does not exist in its `Map`
         * before its [value] has been assigned, or after a call to [remove].
         */
        @RO Boolean exists;

        /**
         * The value associated with the `Entry`'s key.
         *
         * The `value` property is not settable and the `Entry` is not removable if the containing
         * `Map` is not [inPlace].
         *
         * If the `Entry` does not [exist](exists), then the value is not readable; an attempt to get
         * the value of an will raise an `OutOfBounds`
         *
         * @throws OutOfBounds  if an attempt is made to read the value of the `Entry` when [exists]
         *                      is False
         * @throws ReadOnly     if an attempt is made to write the value of the `Entry` and the map
         *                      is not [inPlace], or does not support mutation
         */
        Value value;

        /**
         * Remove the `Entry` from its `Map`.
         *
         * The `Entry` is not removable if the containing `Map` is not [inPlace].
         *
         * @throws OutOfBounds  if an attempt is made to delete the `Entry` when [exists] is `False`
         * @throws ReadOnly     if an attempt is made to delete the `Entry` and the Map is not [inPlace],
         *                      or does not support mutation
         */
        void delete() {
            throw new ReadOnly();
        }

        /**
         * If the `Entry` is a temporary object, for example an `Entry` that can be re-used to represent
         * multiple logical entries within an `Entry` iterator, then obtain a reference to the same
         * `Entry` that is _not_ temporary, allowing the resulting reference to be held indefinitely.
         *
         * Holding the `Entry` will likely prevent the garbage-collection of the `Map`.
         *
         * @return an `Entry` object that can be retained indefinitely; changes to the values in the
         *         `Map` may or may not be reflected in the returned `Entry`
         */
        Entry reify() {
            return this;
        }

        @Override
        String toString() {
            return $"{key}={value}";
        }

        /**
         * Two `Entry` objects are equal iff they contain equal keys, and equal values (or neither
         * exists).
         */
        static <CompileType extends Entry> Boolean equals(CompileType entry1, CompileType entry2) {
            return entry1.key == entry2.key &&
                    entry1.exists ? entry2.exists && entry1.value == entry2.value : !entry2.exists;
        }
    }

    // ----- Entry operations ----------------------------------------------------------------------

    /**
     * Apply the specified function to the entry for the specified key. If that key does not exist
     * in the `Map`, then an `Entry` is provided to it whose [Entry.exists] property is `False`;
     * setting the value of the entry will cause the entry to _appear in_  (be added to) the `Map`,
     * which is to say that the `Map` will now contain an entry for that key with that value.
     * Similarly, calling [Entry.delete] on the entry will ensure that the key and any associated
     * value are now _not_ present in the `Map`.
     *
     * @param key      specifies the key of the entry to process
     * @param compute  the function that will operate against the Entry
     *
     * @return the result of the specified function
     *
     * @throws ReadOnly  if the `Map` does not support in-place mutation and the `compute` function
     *                   attempts to modify an entry
     */
    @Concurrent
    <Result> Result process(Key                                key,
                            function Result(Entry<Key, Value>) compute) {
        Entry<Key, Value> entry  = new KeyEntry(this, key);
        Result            result = compute(entry);
        return result;
    }

    /**
     * Apply the specified function to the Entry objects for the specified keys.
     *
     * @param keys     specifies which keys to process
     * @param compute  the function that will operate against each Entry
     *
     * @return a `Map` containing the results from the specified function applied against the
     *         entries for the specified keys
     *
     * @throws ReadOnly  if the `Map` does not support in-place mutation and the `compute` function
     *                   attempts to modify an entry
     */
    @Concurrent
    <Result> Map!<Key, Result> processAll(Iterable<Key>                      keys,
                                          function Result(Entry<Key, Value>) compute) {
        Int resultCount = keys.size;
        if (resultCount == 0) {
            return [];
        }

        Key[]    resultKeys = keys.toArray();
        Result[] resultVals = new Result[](resultCount);
        CursorEntry<Key, Value> entry  = new CursorEntry(this);
        for (Key key : resultKeys) {
            resultVals.add(compute(entry.advance(key)));
        }
        return new ListMap<Key, Result>(resultKeys, resultVals);
    }

    /**
     * Apply the specified function to the entry for the specified key, iff such an entry exists in
     * the `Map`.
     *
     * @param key      specifies which entry to process
     * @param compute  the function that will operate against the [Entry], iff the entry
     *                 already exists in the map
     *
     * @return a conditional True and the result of the specified function iff the entry exists;
     *         otherwise a conditional False
     *
     * @throws ReadOnly  if the `Map` does not support in-place mutation and the `compute` function
     *                   attempts to modify an entry
     */
    @Concurrent
    <Result> conditional Result processIfPresent(Key                                key,
                                                 function Result(Entry<Key, Value>) compute) {
        // this implementation can be overridden to combine the contains() and process() into
        // a single step
        if (contains(key)) {
            return True, process(key, compute);
        } else {
            return False;
        }
    }

    /**
     * Obtain the existing value for the specified key, or if the key does not already exist in the
     * `Map`, create a new entry in the `Map` containing the specified key and the result of the
     * specified function.
     *
     * @param key      specifies the key that may or may not already be present in the map
     * @param compute  the function that will be called iff the key does not already exist in the
     *                 `Map`, and which will return the new value to associate with that key
     *
     * @return the value for the specified key, which may have already existed in the `Map`, or may
     *         have just been calculated by the specified function and placed into the map
     * @return True iff the entry was absent, and this operation created it
     *
     * @throws ReadOnly  if the `Map` does not allow or support the requested mutating operation
     */
    (Value, Boolean) computeIfAbsent(Key              key,
                                     function Value() compute) {
        return process(key, entry -> {
            if (entry.exists) {
                return (entry.value, False);
            }

            if (!inPlace) {
                throw new ReadOnly();
            }

            Value value = compute();
            entry.value = value;
            return (value, True);
        });
    }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength(
            String?                 sep         = ", ",
            String?                 pre         = "[",
            String?                 post        = "]",
            String?                 keySep      = "=",
            Int?                    limit       = Null,
            String?                 trunc       = "...",
            function String(Key)?   keyRender   = Null,
            function String(Value)? valueRender = Null,
            ) {
        Int entryCount   = size;
        Int displayCount = limit ?: entryCount;
        Int count        = (pre?.size : 0) + (post?.size : 0) +
                           ((sep?.size : 0) + (keySep?.size : 0)) * displayCount;

        if (keyRender == Null && valueRender == Null &&
                Key.is(Type<Stringable>) && Value.is(Type<Stringable>)) {
            var iter = iterator();
            if (displayCount < entryCount) {
                iter   = iter.limit(displayCount);
                count += trunc?.size : 0;
            }
            for (val entry : iter) {
                count += entry.key.estimateStringLength() + entry.value.estimateStringLength();
            }
            return count;
        }

        // no inexpensive way to guess
        return count + displayCount*8;
    }

    /**
     * Append the contents of the `Map` to the specified buffer.
     *
     * @param buf          the buffer to append to
     * @param sep          the separator string that will be placed between entries
     * @param pre          the string to precede the entries
     * @param post         the string to follow the entries
     * @param keySep       the separator string that will be placed between keys and values
     * @param limit        the maximum number of elements to include in the string
     * @param trunc        the string that indicates that the maximum number of elements was exceeded
     * @param renderKey    the optional function to use to render each key to a string
     * @param renderValue  the optional function to use to render each value to a string
     *
     * @return the buffer
     */
    @Override
    Appender<Char> appendTo(
            Appender<Char>          buf,
            String?                 sep         = ", ",
            String?                 pre         = "[",
            String?                 post        = "]",
            String?                 keySep      = "=",
            Int?                    limit       = Null,
            String?                 trunc       = "...",
            function String(Key)?   keyRender   = Null,
            function String(Value)? valueRender = Null,
            ) {
        pre?.appendTo(buf);

        function Appender<Char>(Key) appendKey =
            keyRender != Null        ? (k -> keyRender(k).appendTo(buf)) :
            Key.is(Type<Stringable>) ? (k -> k.appendTo(buf))            :
                                       (k -> k.is(Stringable) ? k.appendTo(buf) : buf.addAll(k.toString()));

        function Appender<Char>(Value) appendValue =
            valueRender != Null        ? (v -> valueRender(v).appendTo(buf)) :
            Value.is(Type<Stringable>) ? (v -> v.appendTo(buf))              :
                                         (v -> v.is(Stringable) ? v.appendTo(buf) : buf.addAll(v.toString()));

        if (limit == Null || limit < 0) {
            limit = MaxValue;
        }

        Loop: for ((Key key, Value value) : this) {
            if (!Loop.first) {
                sep?.appendTo(buf);
            }

            if (Loop.count >= limit) {
                trunc?.appendTo(buf);
                break;
            }

            appendKey(key);
            keySep?.appendTo(buf);
            appendValue(value);
        }

        post?.appendTo(buf);
        return buf;
    }

    @Override
    String toString(
            String?                 sep         = ", ",
            String?                 pre         = "[",
            String?                 post        = "]",
            String?                 keySep      = "=",
            Int?                    limit       = Null,
            String?                 trunc       = "...",
            function String(Key)?   keyRender   = Null,
            function String(Value)? valueRender = Null,
            ) {
        StringBuffer buf = new StringBuffer(
            estimateStringLength(sep, pre, post, keySep, limit, trunc, keyRender, valueRender));
        return appendTo(buf, sep, pre, post, keySep, limit, trunc, keyRender, valueRender).toString();
    }

    // ----- equality ------------------------------------------------------------------------------

    /**
     * Two maps are equal iff they are they contain the same keys, and the value associated with
     * each key in the first `Map` is equal to the value associated with the same key in the second
     * `Map`.
     */
    static <CompileType extends Map> Boolean equals(CompileType map1, CompileType map2) {
        if (map1.size != map2.size) {
            return False;
        }

        for ((CompileType.Key key1, CompileType.Value value1) : map1) {
            if (CompileType.Value value2 := map2.get(key1), value2 == value1) {
                continue;
            }
            return False;
        }

        return True;
    }
}