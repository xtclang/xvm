/**
 * `DeferredMap` is a base class for deferred results from various operation on a `Map`, such as
 * `map()` and `filter()`.
 */
@Abstract class DeferredMap<Key, Value, FromValue>
        implements Map<Key, Value> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `DeferredMap` based on an original [Map].
     *
     * @param original  the [Map] from which this `Map`'s contents will be drawn
     */
    construct(Map<Key, FromValue> original) {
        this.original = original;
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * The `Map` from which the contents of this `Map` will be drawn, or `Null` after this
     * Map has been reified (to allow memory to be collected).
     */
    protected Map<Key, FromValue>? original;

    /**
     * The cached reified copy of this `Map`.
     */
    protected @RO Map<Key, Value> reified.get() {
        return actualReified ?: ensureReified();
    }

    /**
     * The actual storage for the reified copy of this `Map`.
     */
    private Map<Key, Value>? actualReified = Null;

    /**
     * @return the reified copy of this Map to cache
     */
    protected <Result extends Map<Key, Value>> Result ensureReified(
            MapCollector<Key, Value, Result>? collector = Null) {
        Result result;
        if (Map<Key, FromValue> original ?= original) {
            collector ?:= defaultCollector().as(MapCollector<Key, Value, Result>);
            var accumulator = collector.init();
            evaluateInto(accumulator);
            actualReified = result <- collector.reduce(accumulator);
            postReifyCleanup();
        } else { // already reified
            if (collector == Null) {
                result = reified.as(Result);
            } else {
                var accumulator = collector.init();
                accumulator.putAll(reified);
                result = collector.reduce(accumulator);
            }
        }
        return result;
    }

    /**
     * @return the reified copy of this Map to use for mutation operations
     *
     * @throws ReadOnly if the `DeferredMap` has not been reified and the [original] `Map` is
     *         [Map.inPlace]
     */
    protected Map<Key, Value> ensureReifiedForMutation() {
        if (Map<Key, FromValue> original ?= original, original.inPlace) {
            throw new ReadOnly("mutating Map operation on a view requires Map.reify()");
        }
        return reified;
    }

    /**
     * This is the method that creates the data structure that will hold the reified result.
     * Sub-classes can override this to use a specific data structure, or pre-size the data
     * structure accordingly.
     *
     * @return the empty data structure that will be filled to create the reified copy of the
     *         contents of this map
     */
    protected Map<Key, Value> instantiateEmptyReified() {
        return new ListMap();
    }

    /**
     * Clean up any references that can hold memory that were only necessary up until and including
     * the process of reification.
     */
    protected void postReifyCleanup()
        {
        original          = Null;
        cursorFromEntry   = Null;
        discreteFromEntry = Null;
        }

    /**
     * Indicate whether this `DeferredMap` has already cached its reified contents.
     */
    protected Boolean alreadyReified.get() = original == Null;

    /**
     * This is the method that allows the contents of this `DeferredMap` to be iterated, without
     * creating a reified copy of its data. Each sub-class must provide an implementation.
     *
     * @return an [Iterator] that provides the contents of this `DeferredMap`, drawn from the
     *         original [Map]
     */
    protected @Abstract Iterator<Entry<Key, Value>> unreifiedIterator();

    /**
     * This method is used to efficiently reify a chain of `DeferredMap` objects, such as with
     * a sequence of calls like `map(...).filter(...)` etc.
     *
     * @param accumulator  the [MapAppender] to append all of the key/value pairs to, from this
     *                     `DeferredMap`
     */
    protected void evaluateInto(MapAppender<Key, Value> accumulator) {
        if (alreadyReified) {
            accumulator.putAll(reified);
        } else {
            for (Entry<Key, Value> entry : unreifiedIterator()) {
                accumulator.put(entry.key, entry.value);
            }
        }
    }

    /**
     * Obtain a [Map.Entry] representing the a `Key` from the [original] `Map`.
     *
     * @param key  the `Key`
     *
     * @return a temporary, self-recycling `Entry` that refers to the `Entry` in the [original]
     *         `Map` that has the specified `Key`
     */
    protected CursorFromEntry<Key, FromValue> fromEntry(Key key) {
        CursorFromEntry<Key, FromValue> entry;
        if (entry ?= cursorFromEntry) {
            cursorFromEntry = Null;
            entry.advance(key);
        } else {
            entry = new CursorFromEntry(this:private, key);
        }
        return entry;
    }

    /**
     * Obtain a [Map.Entry] representing the a `Key` and `FromValue` from the [original] `Map`.
     *
     * @param key    the `Key`
     * @param value  the `Value` associated with the `Key`
     *
     * @return a temporary, self-recycling `Entry` that contains the specified `Key` and `Value`
     */
    protected DiscreteFromEntry<Key, FromValue> fromEntry(Key key, FromValue value) {
        DiscreteFromEntry<Key, FromValue> entry;
        if (entry ?= discreteFromEntry) {
            discreteFromEntry = Null;
            entry.advance(key, value);
        } else {
            entry = new DiscreteFromEntry(this:private, key, value);
        }
        return entry;
    }

    /**
     * A self-recycling "cursor" `Entry<Key, FromValue>` that can be used as an `Entry` cursor.
     */
    protected static class CursorFromEntry<Key, Value>((private DeferredMap) deferredMap, Key key)
            extends CursorEntry<Key, Value>(deferredMap.original.as(Map<Key, Value>), key)
            implements Closeable {
        @Override
        void close(Exception? cause = Null) {
            deferredMap.cursorFromEntry = this;
        }
    }

    /**
     * A self-recycling "discrete" `Entry<Key, FromValue>` that can be used as a key/value cursor.
     */
    protected static class DiscreteFromEntry<Key, Value>((private DeferredMap) deferredMap,
                                                         Key                   key,
                                                         Value                 value)
            extends DiscreteEntry<Key, Value>(key, value, readOnly=True)
            implements Closeable {
        /**
         * @param key    the `Key` to advance to
         * @param value  the `Value` associated with the `Key` to advance to
         */
        DiscreteFromEntry advance(Key key, Value value) {
            this.key = key;
            this.val = value;
            return this;
        }

        @Override
        Entry<Key, Value> reify() = new DiscreteEntry(key, value);

        @Override
        void close(Exception? cause = Null) {
            deferredMap.discreteFromEntry = this;
        }
    }

    /**
     * A cached instance of a self-recycling "cursor" `Entry<Key, FromValue>`.
     */
    protected CursorFromEntry<Key, FromValue>? cursorFromEntry = Null;

    /**
     * A cached instance of a self-recycling "discrete" `Entry<Key, FromValue>`.
     */
    protected DiscreteFromEntry<Key, FromValue>? discreteFromEntry = Null;

    // ----- Map interface ------------------------------------------------------------------

    @Override
    @RO Boolean inPlace.get() = original?.inPlace : reified.inPlace;

    @Override
    conditional Orderer? ordered() = original?.ordered() : reified.ordered();

    @Override
    conditional Int knownSize() {
        if (Map<Key, FromValue> original ?= original) {
            // this implementation only knows the size iff the original is known to be empty;
            // sub-classes that have more knowledge of the relationship between the size of the
            // original and the size of the reified result should override this method accordingly
            if (Int origSize := original.knownSize(), origSize == 0) {
                return True, 0;
            }
            return False;
        } else {
            return reified.knownSize();
        }
    }

    @Override
    @RO Int size.get() {
        // sub-classes should implement this if they can do so efficiently without reifying
        return reified.size;
    }

    @Override
    @RO Boolean empty.get() {
        // sub-classes should implement this if they can do so efficiently without reifying
        if (Int origSize := original?.knownSize(), origSize == 0) {
            return True;
        }
        return reified.empty;
    }

    @Override
    @Concurrent
    Boolean contains(Key key) {
        // sub-classes should implement this if they can do so efficiently without reifying
        if (Map<Key, FromValue> original ?= original, !original.contains(key)) {
            return False;
        }
        return reified.contains(key);
    }

    @Override
    conditional Value get(Key key) {
        // sub-classes should implement this if they can do so efficiently without reifying
        return reified.get(key);
    }

    @Override
    Iterator<Entry<Key, Value>> iterator() {
        // assume that some percentage of DeferredMaps are created and then iterated exactly
        // once, in which case it's wasteful to realize the results; conversely, if multiple
        // iterations occur, then realize the results
        private Boolean iteratedAtLeastOnce = False;
        if (!iteratedAtLeastOnce && !alreadyReified) {
            iteratedAtLeastOnce = True;
            return unreifiedIterator();
        }
        return reified.iterator();
    }

    @Override
    @Lazy Set<Key> keys.calc() = new MapKeys<Key, Value>(this);

    @Override
    @Lazy Collection<Value> values.calc() = new MapValues<Key, Value>(this);

    @Override
    @Lazy Collection<Entry<Key, Value>> entries.calc() = new MapEntries<Key, Value>(this);

    @Override
    Map<Key, Value> filter(function Boolean(Entry<Key, Value>) match) {
        return alreadyReified
                ? reified.filter(match)
                : super(match);
    }

    @Override
    (Map<Key, Value> matches, Map<Key, Value> misses) partition(function Boolean(Entry<Key, Value>) match) {
        return alreadyReified
                ? reified.partition(match)
                : super(match);
    }

    @Override
    <ToValue> Map<Key, ToValue> map(function ToValue(Entry<Key, Value>) transform) {
        return alreadyReified
            ? reified.map(transform)
            : super(transform);
    }

    @Override
    Map<Key, Value> sorted(Orderer? order = Null) = reified.sorted(order);

    @Override
    <Result extends Map<Key, Value>> Result sortedByEntry(
            function Ordered(Entry<Key, Value>, Entry<Key, Value>) order,
            MapCollector<Key, Value, Result>? collector = Null) {
        return reified.sortedByEntry(order, collector);
    }

    @Override
    MapCollector<Key, Value, Map<Key, Value>> defaultCollector() {
        // unwind to the original original map
        Map? original = this.original;
        while (original.is(DeferredMap<Key, Object>)) {
            original = original.original;
        }

        if (original != Null) {
            if (Value == original.Value) {
                return original.defaultCollector();
            }

            if (original.is(Replicable)) {
                // it's Replicable but with a different Value type, so rewrite the type with the
                // correct Value type
                val origType = &original.actualType;
                Type[] typeParams;
                if (typeParams := origType.parameterized()) {
                    typeParams = typeParams.replace(1, Value);
                } else {
                    typeParams = [Key, Value];
                }
                // now instantiate the Map with the rewritten type and ask it for its MapCollector;
                // this isn't winning awards for efficiency, but it's probably the MapCollector that
                // the developer would want to be used, since it comes from the class of the
                // original Map
                try {
                    val newType = origType.parameterize(typeParams);
                    if (val instantiate := newType.defaultConstructor(original.is(Inner) ? original.outer : Null)) {
                        return instantiate().as(Map<Key, Value>).defaultCollector();
                    }
                } catch (reflect.InvalidType _) {}
            }
        }

        return super();
    }

    @Override
    <Result extends Map<Key, Value>> Result reify(MapCollector<Key, Value, Result>? collector = Null) {
        return ensureReified(collector);
    }

    @Override
    Map<Key, Value> put(Key key, Value value) = ensureReifiedForMutation().put(key, value);

    @Override
    @Op("[]=") void putInPlace(Key key, Value value) = ensureReifiedForMutation().putInPlace(key, value);

    @Override
    Map<Key, Value> putAll(Map<Key, Value> that) = ensureReifiedForMutation().putAll(that);

    @Override
    conditional Map<Key, Value> putIfAbsent(Key key, Value value) =
            ensureReifiedForMutation().putIfAbsent(key, value);

    @Override
    conditional Map<Key, Value> replace(Key key, Value valueOld, Value valueNew) =
            ensureReifiedForMutation().replace(key, valueOld, valueNew);

    @Override
    Map<Key, Value> remove(Key key) = ensureReifiedForMutation().remove(key);

    @Override
    conditional Map<Key, Value> remove(Key key, Value value) = ensureReifiedForMutation().remove(key, value);

    @Override
    @Op("-") Map<Key, Value> removeAll(Iterable<Key> keys) = ensureReifiedForMutation().removeAll(keys);

    @Override
    (Map<Key, Value>, Int) removeAll(function Boolean(Entry<Key, Value>) shouldRemove) =
            ensureReifiedForMutation().removeAll(shouldRemove);

    @Override
    Map<Key, Value> clear() = ensureReifiedForMutation().clear();
}