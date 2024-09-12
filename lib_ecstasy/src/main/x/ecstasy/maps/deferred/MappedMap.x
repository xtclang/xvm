/**
 * `MappedMap` is the deferred result of a `map()` operation on a `Map`.
 */
class MappedMap<Key, Value, FromValue>
        extends DeferredMap<Key, Value, FromValue> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a MappedMap based on an original `Map` and a mapping function.
     *
     * @param original   the unmapped `Map`
     * @param transform  the mapping function
     */
    construct(Map<Key, FromValue> original, Transformer transform) {
        construct DeferredMap(original);
        this.transform = transform;
    }


    // ----- internal ------------------------------------------------------------------------------

    typedef Map.Entry<Key, Value>     as ToEntry;
    typedef Map.Entry<Key, FromValue> as FromEntry;
    typedef function Value(FromEntry) as Transformer;

    /**
     * The mapping function, or Null after it has been applied (which allows memory to be
     * collected).
     */
    protected Transformer? transform;

    @Override
    protected void postReifyCleanup() {
        transform = Null;
        super();
    }

    @Override
    protected Iterator<Entry<Key, Value>> unreifiedIterator() {
        CursorEntry<Key, Value> toEntry  = new CursorEntry(this);
        Iterator<FromEntry>     fromIter = original?.iterator() : assert;
        return new Iterator<ToEntry>() {
            @Override
            conditional Element next() {
                if (FromEntry fromEntry := fromIter.next()) {
                    // in theory, we have access to the value as well at this point (since we have
                    // "the entry"), but there's no way to tell if fetching that value has hidden
                    // cost, and this unreified iterator may be being used just to iterate the keys,
                    // so allow the value access to be lazy (implemented by the CursorEntry)
                    return True, toEntry.advance(fromEntry.key);
                }
                return False;
            }

            @Override Int count() = fromIter.count();
            @Override Boolean knownDistinct() = True;
            @Override Boolean knownEmpty() = fromIter.knownEmpty();
            @Override conditional Int knownSize() = fromIter.knownSize();
        };
    }

    @Override
    protected void evaluateInto(MapAppender<Key, Value> accumulator) {
        if (val nextDeferred := original.is(DeferredMap<Key, FromValue>),
                Transformer transform ?= transform) {
            static class ApplyMapping<Key, ToValue, FromValue>(
                    DeferredMap<Key, ToValue, FromValue>    deferredMap,
                    MapAppender<Key, ToValue>               accumulator,
                    function ToValue(Entry<Key, FromValue>) transform
                    )
                    implements MapAppender<Key, FromValue> {
                @Override
                ApplyMapping put(Key key, FromValue value) {
                    using (val entry = deferredMap.fromEntry(key, value)) {
                        accumulator = accumulator.put(key, transform(entry));
                    }
                    return this;
                }

                @Override
                ApplyMapping putAll(Map<Key, FromValue> that) {
                    for (val entry : that.iterator()) {
                        accumulator = accumulator.put(entry.key, transform(entry));
                    }
                    return this;
                }
            }
            nextDeferred.evaluateInto(new ApplyMapping<Key, Value, FromValue>(this, accumulator, transform));
        } else {
            super(accumulator);
        }
    }


    // ----- Map interface ------------------------------------------------------------------

    @Override
    conditional Int knownSize() = original?.knownSize() : reified.knownSize();

    @Override
    @RO Int size.get() = original?.size : reified.size;

    @Override
    @RO Boolean empty.get() = original?.empty : reified.empty;

    @Override
    Boolean contains(Key key) = original?.contains(key) : reified.contains(key);

    @Override
    conditional Value get(Key key) {
        if (Map<Key, FromValue> original ?= original,
                Transformer transform ?= transform) {
            if (FromValue value := original.get(key)) {
                using (val entry = fromEntry(key, value)) {
                    return True, transform(entry);
                }
            } else {
                return False;
            }
        } else {
            return reified.get(key);
        }
    }
}