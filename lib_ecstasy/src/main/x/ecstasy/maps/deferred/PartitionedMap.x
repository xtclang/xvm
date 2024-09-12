/**
 * `PartitionedMap` is the deferred result of a `partition()` operation on a `Map`.
 */
class PartitionedMap<Key, Value>
        extends DeferredMap<Key, Value, Value> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a PartitionedMap based on an original `Map` and a filter.
     *
     * @param original  the unpartitioned `Map`
     * @param include   the inclusion filter
     */
    construct(Map<Key, FromValue> original, function Boolean(Entry<Key, Value>) include) {
        construct DeferredMap(original);
        this.primary = True;
        this.include = include;
    } finally {
        this.buddy   = new PartitionedMap<Key, Value>(this);
    }

    /**
     * Construct the "misses" PartitionedMap corresponding to the specified "include"
     * PartitionedMap. This collection represents the missing entries.
     *
     * @param buddy  the primary PartitionedMap instance that this is the "inverse" buddy of
     */
    protected construct(PartitionedMap<Key, Value> buddy) {
        construct DeferredMap(buddy.original ?: assert);
        function Boolean(Entry<Key, Value>) hit  = buddy.include ?: assert;
        function Boolean(Entry<Key, Value>) miss = entry -> !hit(entry);
        this.primary = False;
        this.include = miss;
        this.buddy   = buddy;
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * The partitioning function, or `Null` after it has been partitioned (which allows memory to be
     * collected).
     */
    protected function Boolean(Entry<Key, Value>)? include;

    /**
     * The buddy partition.
     */
    protected PartitionedMap<Key, Value>? buddy;

    /**
     * A property used to expose the existence of the buddy partition after construction.
     */
    PartitionedMap<Key, Value> inverse.get() = buddy ?: assert;

    /**
     * `True` for the first of the two PartitionedMap buddies instantiated (the "matches); `False`
     * for the second (the "misses").
     */
    protected/private @Final Boolean primary;

    /**
     * This property may be set to the reified data by the buddy.
     */
    protected Map<Key, Value>? data;

    @Override
    protected <Result extends Map<Key, Value>> Result ensureReified(
            MapCollector<Key, Value, Result>? collector = Null) {
        Map<Key, Value> result;
        if (result ?= data) {
            // our buddy already did the reify work and pushed our reified data to us; just use that
        } else if (primary || buddy?.alreadyReified : True) {
            // if "this" is the primary partition aka "matches", then the actual partitioning work
            // is done here; otherwise, either we no longer have a buddy, or the buddy already did
            // its reification work without us doing our reification work; this can happen (to
            // either buddy) if we have already been called to evaluateInto(), and as a side-effect
            // of that call, we reified the buddy, but being a smidge too clever, we didn't reify
            // ourselves (optimizing for the assumption that no one would ask the same question
            // again)
            result = super(collector);
        } else {
            // this is not the primary partition, i.e. this is the "misses"; tell the primary to do
            // the actual partitioning work, and then use the reified result that it gives us
            buddy?.reify(collector);
            assert result ?= data;
        }

        postReifyCleanup();
        return result.as(Result);
    }

    @Override
    protected void postReifyCleanup() {
        data = Null;
        // if we're the non-primary, then we're always allowed to forget the primary after
        // reification because it never relies on us; if we're the primary, our buddy relies on us
        // until it has reified
        if (!primary || buddy?.alreadyReified) {
            include = Null;
            super();
        }
    }

    @Override
    protected Iterator<Entry<Key, Value>> unreifiedIterator() {
        return original?.iterator().filter(include?) : assert;
    }

    @Override
    protected void evaluateInto(MapAppender<Key, Value> accumulator) {
        if (DeferredMap<Key, Value> nextDeferred := original.is(DeferredMap<Key, Value>),
                function Boolean(Entry<Key, Value>) include ?= include) {
            static class ApplyPartition<Key, Value>(DeferredMap<Key, Value, Value>          deferredMap,
                                                    MapAppender<Key, Value>                 accumulator,
                                                    function Boolean(Map.Entry<Key, Value>) include)
                    implements MapAppender<Key, Value> {
                @Override
                ApplyPartition put(Key key, Value value) {
                    using (val entry = deferredMap.fromEntry(key, value)) {
                        if (include(entry)) {
                            accumulator = accumulator.put(key, value);
                        }
                    }
                    return this;
                }

                @Override
                ApplyPartition putAll(Map<Key, Value> that) {
                    for (val entry : that.iterator()) {
                        if (include(entry)) {
                            accumulator = accumulator.put(entry.key, entry.value);
                        }
                    }
                    return this;
                }
            }
            nextDeferred.evaluateInto(new ApplyPartition<Key, Value>(this, accumulator, include));
        } else {
            super(accumulator);
        }
    }


    // ----- Map interface ------------------------------------------------------------------

    @Override
    Boolean contains(Key key) {
        if (Map<Key, FromValue> original ?= original,
                function Boolean(Entry<Key, Value>) include ?= include) {
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
                function Boolean(Entry<Key, Value>) include ?= include) {
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
}