/**
 * `FilteredMap` is the deferred result of a `filter()` operation on a `Map`.
 */
class FilteredMap<Key, Value>
        extends DeferredMap<Key, Value, Value> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a FilteredMap based on an original `Map` and a filter.
     *
     * @param original  the unfiltered `Map`
     * @param include   the inclusion filter
     */
    construct(Map<Key, FromValue> original, function Boolean(Entry<Key, FromValue>) include) {
        construct DeferredMap(original);
        this.include = include;
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * The filtering function, or `Null` after it has been filtered (which allows memory to be
     * collected).
     */
    protected function Boolean(Entry<Key, FromValue>)? include;

    @Override
    protected void postReifyCleanup() {
        include = Null;
        super();
    }

    @Override
    protected Iterator<Entry<Key, Value>> unreifiedIterator() {
        return original?.iterator().filter(include?) : assert;
    }

    @Override
    protected void evaluateInto(MapAppender<Key, Value> accumulator) {
        if (DeferredMap<Key, Value> nextDeferred := original.is(DeferredMap<Key, Value>),
                function Boolean(Entry<Key, FromValue>) include ?= include) {
            static class ApplyFilter<Key, Value>(DeferredMap<Key, Value, Value>      deferredMap,
                                                 MapAppender<Key, Value>             accumulator,
                                                 function Boolean(Entry<Key, Value>) include)
                    implements MapAppender<Key, Value> {
                @Override
                ApplyFilter put(Key key, Value value) {
                    using (val entry = deferredMap.fromEntry(key, value)) {
                        if (include(entry)) {
                            accumulator = accumulator.put(key, value);
                        }
                    }
                    return this;
                }

                @Override
                ApplyFilter putAll(Map<Key, Value> that) {
                    for (val entry : that.iterator()) {
                        if (include(entry)) {
                            accumulator = accumulator.put(entry.key, entry.value);
                        }
                    }
                    return this;
                }
            }
            nextDeferred.evaluateInto(new ApplyFilter<Key, Value>(this, accumulator, include));
        } else {
            super(accumulator);
        }
    }


    // ----- Map interface ------------------------------------------------------------------

    @Override
    Boolean contains(Key key) {
        if (Map<Key, FromValue> original ?= original,
                function Boolean(Entry<Key, FromValue>) include ?= include) {
            if (FromValue value := original.get(key)) {
                using (val entry = fromEntry(key, value)) {
                    return include(entry);
                }
            } else {
                return False;
            }
        } else {
            return reified.contains(key);
        }
    }

    @Override
    conditional Value get(Key key) {
        if (Map<Key, FromValue> original ?= original,
                function Boolean(Entry<Key, FromValue>) include ?= include) {
            if (FromValue value := original.get(key)) {
                using (val entry = fromEntry(key, value)) {
                    return include(entry)
                            ? (True, value)
                            : False;
                }
            } else {
                return False;
            }
        } else {
            return reified.get(key);
        }
    }

    @Override
    Map<Key, Value> filter(function Boolean(Entry<Key, Value>) match) {
        if (Map<Key, FromValue> original ?= original,
                function Boolean(Entry<Key, FromValue>) include ?= include) {
            import deferred.FilteredMap;
            return new FilteredMap<Key, Value>(original, e -> include(e) && match(e));
        } else {
            return super(match);
        }
    }
}