/**
 * In-memory store for a DBMap.
 */
class MapStore<Key extends immutable Const, Value extends immutable Const>
    (DBObjectInfo info, Appender<String> errs)
        extends ObjectStore(info, errs)
        delegates Map<Key, Value>(contents)
    {
    @Inject("clock") Clock clock;

    // ----- master view ---------------------------------------------------------------------------

    /**
     * The key/values and dates.
     */
    protected Map<Key, Value> contents = new HashMap<Key, Value>();
    protected Map<Key, Time>  dates    = new HashMap<Key, Time>();

    @Override
    MapStore put(Key key, Value value)
        {
        contents.put(key, value);
        dates.put(key, clock.now);
        return this;
        }

    /**
     * @return an immutable keys snapshot
     */
    Set<Key> keysSnapshot()
        {
        return new HashSet<Key>(keys).makeImmutable();
        }

    /**
     * @return an immutable keys snapshot
     */
    Collection<Value> valuesSnapshot()
        {
        return values.toArray(Constant);
        }


    // ----- transactional -------------------------------------------------------------------------

    /**
     * Transactional changes keyed by the client id.
     */
    private Map<Int, TxChange> contentsAt = new SkiplistMap();

    Int sizeAt(Int clientId)
        {
        Int size = contents.size;
        if (TxChange change := contentsAt.get(clientId))
            {
            return size - change.removed.size + change.added.size;
            }
        return size;
        }

    Boolean emptyAt(Int clientId)
        {
        return sizeAt(clientId) == 0;
        }

    Boolean containsAt(Int clientId, Key key)
        {
        return getAt(clientId, key);
        }

    conditional Value getAt(Int clientId, Key key)
        {
        return contentsAt.computeIfAbsent(clientId, () -> this.MapStore.new TxChange()).get(key);
        }

    Set<Key> keysAt(Int clientId)
        {
        Set<Key> keys = new HashSet(contents.keys);

        if (TxChange change := contentsAt.get(clientId))
            {
            keys.removeAll(change.removed.keys);
            keys.addAll(change.added.keys);
            }
        return keys.makeImmutable();
        }

    void putAt(Int clientId, Key key, Value value)
        {
        contentsAt.computeIfAbsent(clientId, () -> this.MapStore.new TxChange()).put(key, value);
        }

    void removeAt(Int clientId, Key key)
        {
        contentsAt.computeIfAbsent(clientId, () -> this.MapStore.new TxChange()).remove(key);
        }

    @Override
    void apply(Int clientId)
        {
        if (TxChange change := contentsAt.get(clientId))
            {
            for (Key key : change.removed.keys)
                {
                contents.remove(key);
                }

            Time now = clock.now;
            for ((Key key, Value value) : change.added)
                {
                contents.put(key, value);
                dates   .put(key, now);
                }

            contentsAt.remove(clientId);
            }
        }

    @Override
    void discard(Int clientId)
        {
        contentsAt.remove(clientId);
        }

    class TxChange
            implements oodb.DBMap<Key, Value>.TxChange
        {
// TODO GG: consider holding a miss as "None" singleton
//       HashMap<Key, Record<Value|None old, Value|None new>> read    = new HashMap();
        HashMap<Key, Value> read    = new HashMap();

        @Override
        HashMap<Key, Value> added   = new HashMap();

        @Override
        HashMap<Key, Value> removed = new HashMap();

        @Override
        oodb.DBMap pre.get()
            {
            TODO("read-only new DBMap(...)");
            }

        @Override
        oodb.DBMap post.get()
            {
            TODO("read-only new DBMap");
            }

        conditional Value get(Key key)
            {
            if (removed.contains(key))
                {
                return False;
                }

            Value value;
            if (value := added.get(key))
                {
                return True, value;
                }

            if (value := read.get(key))
                {
                return True, value;
                }

            if (value := contents.get(key))
                {
                read.put(key, value);
                return True, value;
                }

            // REVIEW record misses?
            return False;
            }

        void put(Key key, Value value)
            {
            removed.remove(key);
            added.put(key, value);
            }

        void remove(Key key)
            {
            if (removed.contains(key))
                {
                return;
                }

            Value value;
            if (value := read.get(key))
                {
                removed.put(key, value);
                added.remove(key);
                return;
                }

            if (value := contents.get(key))
                {
                removed.put(key, value);
                added.remove(key);
                return;
                }
            }
        }
    }
