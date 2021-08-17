import json.Doc;
import json.Mapping;

import model.DBObjectInfo;


/**
 * Provides a key/value storage service for JSON formatted data on disk.
 *
 * The disk format follows this style:
 *
 *     [
 *     {"tx":14, [{"d":{...}}, {"k":{...}, "v":{...}}]},
 *     {"tx":17, [{"k":{...}, "v":{...}}, {"k":{...}, "v":{...}}]},
 *     {"tx":18, [{"k":{...}, "v":{...}}, {"k":{...}, "v":{...}}, {"d":{...}}]}
 *     ]
 */
service JsonMapStore<Key extends immutable Const, Value extends immutable Const>
        extends ObjectStore
        implements MapStore<Key, Value>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog          catalog,
              DBObjectInfo     info,
              Appender<String> errs,
              Mapping<Key>     keyMapping,
              Mapping<Value>   valueMapping,
              )
        {
        construct ObjectStore(catalog, info, errs);

        this.keyMapping   = keyMapping;
        this.valueMapping = valueMapping;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The JSON Mapping for the keys in the Map.
     */
    public/protected Mapping<Key> keyMapping;

    /**
     * The JSON Mapping for the values in the Map.
     */
    public/protected Mapping<Value> valueMapping;

    /**
     * Used internally within the in-memory MapStore data structures to represent a deleted
     * key/value pair.
     */
    protected enum Deletion {Deleted}

    /**
     * Used as a "singleton" empty map.
     */
    protected immutable Map<Key, Value|Deletion> NoChanges = Map<>:[];

    @Override
    protected class Changes(Int writeId, Int readId)
        {
        /**
         * A map of inserted and updated key/value pairs, keyed by the internal URI form.
         */
        OrderedMap<Key, Value|Deletion>? mods;

        /**
         * @return a map used to view previously collected modifications, but not intended to be
         *         modified by the caller
         */
        Map<Key, Value|Deletion> peekMods()
            {
            return mods ?: NoChanges;
            }

        /**
         * @return the read/write map used to collect modifications
         */
        OrderedMap<Key, Value|Deletion> ensureMods()
            {
            return mods ?:
                {
                val map = new SkiplistMap<Key, Value|Deletion>();
                mods = map;
                return map;
                };
            }
        }

    @Override
    protected SkiplistMap<Int, Changes> inFlight = new SkiplistMap();

    /**
     * Cached key/transaction/value triples. This is "the database", in the sense that this is the same
     * data that is stored on disk.
     *
     * TODO if the value for a key is stable, replace the nested SkiplistMap with a single Value?
     * TODO need insert vs. update vs. delete information (expensive to build on the fly)
     */
    protected SkiplistMap<Key, SkiplistMap<Int, Value|Deletion>> history = new SkiplistMap();

    /**
     * Cached map sizes, keyed by transaction id.
     */
    protected SkiplistMap<Int, Int> sizeByTx = new SkiplistMap();

    /**
     * Uncommitted transaction information, held temporarily by prepareId. Basically, while a
     * transaction is being prepared, up until it is committed, the information from [Changes.mods]
     * is copied here, so that a view of the transaction as a separate set of changes is not lost;
     * that information is required by the [commit] processing.
     */
    protected SkiplistMap<Int, OrderedMap<Key, Value|Deletion>> modsByTx = new SkiplistMap();


    // ----- ObjectStore life cycle ----------------------------------------------------------------

    // ----- ObjectStore transaction handling ------------------------------------------------------

    @Override
    PrepareResult prepare(Int writeId, Int prepareId)
        {
        // the transaction can be prepared if (a) no transaction has modified this value after the
        // read id, or (b) the "current" value is equal to the read id transaction's value
        assert Changes tx := checkTx(writeId);
        if (tx.peekMods().empty)
            {
            inFlight.remove(writeId);
            return CommittedNoChanges;
            }

        // obtain the transaction modifications (note: we already verified that modifications exist)
        OrderedMap<Key, Value|Deletion> mods = tx.mods ?: assert;

        // first, we need to verify that there are no conflicts, before we attempt to move the data
        // into the "prepareId" slot in the history
        Int readId = tx.readId;
        if (readId != prepareId - 1)
            {
            // interleaving transactions have occurred
            for ((Key key, Value|Deletion value) : mods)
                {
                if (SkiplistMap<Int, Value|Deletion> mapByTx := history.get(key))
                    {
                    assert Int latestTx := mapByTx.last(), latestTx < prepareId;
                    if (latestTx > readId)
                        {
                        assert Value|Deletion latest := mapByTx.get(latestTx);

                        Value|Deletion prev;
                        if (Int prevTx := mapByTx.floor(readId))
                            {
                            assert prev := mapByTx.get(prevTx);
                            }
                        else
                            {
                            // the key did not exist in the readId transaction
                            // TODO GG prev = Deleted;
                            prev = Deletion.Deleted;
                            }

                        if (&prev != &latest)
                            {
                            // the state that this transaction assumes as its starting point was
                            // altered, so the transaction must roll back
                            inFlight.remove(writeId);
                            return FailedRolledBack;
                            }
                        }
                    }
                }
            }

        // now that we have verified that there are no conflicts, the changes need to be "re-homed"
        // into the prepareId transaction in the history, leaving the writeId empty
        Boolean changed = False;
        Int     size    = sizeAt(prepareId-1);
        for ((Key key, Value|Deletion value) : mods)
            {
            if (SkiplistMap<Int, Value|Deletion> mapByTx := history.get(key))
                {
                assert Int            latestTx := mapByTx.last();
                assert Value|Deletion latest   := mapByTx.get(latestTx);

                switch (latest.is(Deletion), value.is(Deletion))
                    {
                    case (False, False):
                        if (&value != &latest)
                            {
                            mapByTx.put(prepareId, value);
                            changed = True;
                            }
                        break;

                    case (False, True):
                        mapByTx.put(prepareId, value);
                        changed = True;
                        ++size;
                        break;

                    case (True, False):
                        mapByTx.put(prepareId, value);
                        changed = True;
                        --size;
                        break;

                    case (True, True):
                        // technically, this should not be possible, but we're deleting something
                        // that doesn't exist, so the deletion modification has no effect
                        mods.remove(key);
                        break;
                    }
                }
            else if (value.is(Deletion))
                {
                // technically, this should not be possible, but we're deleting something that
                // doesn't exist in the history, so the deletion modification has no effect
                mods.remove(key);
                }
            else
                {
                mapByTx = new SkiplistMap();
                mapByTx.put(prepareId, value);
                history.put(key, mapByTx);
                changed = True;
                ++size;
                }
            }

        if (!changed)
            {
            inFlight.remove(writeId);
            return CommittedNoChanges;
            }

        // store off transaction's mods and resulting size
        assert !mods.empty, size >= 0;
        modsByTx.put(prepareId, mods);
        sizeByTx.put(prepareId, size);

        // re-do the write transaction to point to the prepared transaction
        tx.readId   = prepareId;
        tx.prepared = True;
        tx.mods     = Null;
        return Prepared;
        }

    @Override
    MergeResult mergePrepare(Int writeId, Int prepareId, Boolean seal = False)
        {
        MergeResult result = NoMerge;

        if (Changes tx := peekTx(writeId))
            {
            assert !tx.sealed;

            val mods = tx.mods;
            if (mods != Null)
                {
                for ((Key key, Value|Deletion value) : mods)
                    {
                    if (Value prev := latestValue(key, prepareId-1), &value == &prev)
                        {
                        // this part of the transaction is un-doing itself
                        assert val mapByTx := history.get(key);
                        mapByTx.remove(prepareId);
                        continue;
                        }

                    // TODO GG val mapByTx = history.computeIfAbsent(key, () -> new SkiplistMap());
                    val mapByTx = history.computeIfAbsent(key, () -> new SkiplistMap<Int, Value|Deletion>());
                    mapByTx.put(prepareId, value);
                    }

                // REVIEW it is not possible to easily determine a result of CommittedNoChanges
                result = Merged;
                }

            tx.readId   = prepareId;// slide the readId forward to the point that we just prepared
            tx.prepared = True;     // remember that the changed the readId to the prepareId
            tx.mods     = Null;     // the "changes" no longer differs from the historical record
            tx.sealed   = seal;
            }

        return result;
        }

    @Override
    OrderedMap<Int, Doc> commit(OrderedMap<Int, Int> writeIdForPrepareId)
        {
        TODO
        }

    @Override
    void rollback(Int writeId)
        {
        assert isWriteTx(writeId);
        TODO
        }

    @Override
    void retainTx(OrderedSet<Int> inUseTxIds, Boolean force = False)
        {
        TODO
        }

//    /**
//     * Insert, update, and delete the specified data, as part of the specified transaction.
//     *
//     * @param txId      the transaction identifier
//     * @param modified  the keys and values to store
//     * @param deleted   the keys to delete
//     */
//    void commit(Int txId, Map<Key, Value> modified, Key[] deleted)
//        {
//        for ((Key key, Value value) : modified)
//            {
//            store(txId, key, value);
//            }
//
//        for (Key key : deleted)
//            {
//            delete(txId, key);
//            }
//        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Obtain the update-to-date value from the transaction.
     *
     * @param key  the key in the map to obtain the value for
     * @param tx   the transaction's Changes record
     *
     * @return True if the key has a value
     * @return the current value
     */
    protected conditional Value currentValue(Key key, Changes tx)
        {
        if (Value|Deletion value := tx.peekMods().get(key))
            {
            return value.is(Deletion)
                    ? False
                    : (True, value);
            }

        return latestValue(key, tx.readId);
        }

    /**
     * Obtain the original value from when the transaction began.
     *
     * @param key     the key in the map to obtain the value for
     * @param readId  the transaction id to read from
     *
     * @return True if the key has a value as of the specified readId transaction
     * @return the previous value
     */
    protected conditional Value latestValue(Key key, Int readId)
        {
        if (val mapByTx := history.get(key), readId := mapByTx.floor(readId))
            {
            assert Value|Deletion value := mapByTx.get(readId);
            return value.is(Deletion)
                    ? False
                    : (True, value);
            }

        return False;
        }

    /**
     * Obtain the latest committed value.
     *
     * @param key  the key in the map to obtain the value for
     *
     * @return True if the key has a value
     * @return the latest value
     */
    protected conditional Value latestValue(Key key)
        {
        if (val mapByTx := history.get(key))
            {
            assert Int readId := mapByTx.last();
            assert Value|Deletion value := mapByTx.get(readId);
            return value.is(Deletion)
                    ? False
                    : (True, value);
            }

        return False;
        }


    // ----- ObjectStore IO handling ---------------------------------------------------------------

    @Override
    Boolean quickScan()
        {
        if (super() && model != Empty)
            {
            StorageModel quantity = switch (filesUsed)
                {
                case 0x00: assert;
                case 0x0001..0x03FF: Small;
                case 0x0400..0xFFFF: Medium;
                default: Large;
                };

            StorageModel weight = bytesUsed <= 0x03FFFF ? Small : Medium;

            // combine the two measure into the model to actually use
            model = quantity.maxOf(weight);
            }

        return True;
        }

    // ----- Map operations support ----------------------------------------------------------------

    @Override
    Int sizeAt(Int txId)
        {
        checkRead();

        // the adjustments to the size of the transaction will be implied by what is in the Changes
        // record for the transaction, assuming that the transaction is a write ID; otherwise the
        // size is cached by transaction ID in the sizeByTx map
        if (Changes tx := checkTx(txId))
            {
            Int readId = tx.readId;
            Int size   = sizeAt(readId);
            for ((Key key, Value|Deletion value) : tx.peekMods())
                {
                if (value.is(Deletion))
                    {
                    --size;
                    }
                else if (!existsAt(readId, key))
                    {
                    ++size;
                    }
                }
            return size;
            }

        assert isReadTx(txId);
        if (model != Empty, Int closestTxId := sizeByTx.floor(txId))
            {
            assert Int size := sizeByTx.get(txId);
            return size;
            }

        return 0;
        }

    @Override
    Boolean existsAt(Int txId, Key key)
        {
        while (Changes tx := checkTx(txId))
            {
            if (Value|Deletion value := tx.peekMods().get(key))
                {
                // return value != Deleted;   // TODO GG
                // return &value != &Deleted; // TODO GG
                return !value.is(Deletion);
                }

            txId = tx.readId;
            }

        assert isReadTx(txId);
        switch (model)
            {
            case Empty:
                return False;

            case Small:
                // the entire MapStore is cached in the history map
                // SkiplistMap<Key, SkiplistMap<Int, Value|Deletion>> history
                if (val keyHistory := history.get(key), Int ver := keyHistory.floor(txId))
                    {
                    assert Value|Deletion value := keyHistory.get(ver);
                    return !value.is(Deletion);
                    }
                return False;

            case Medium:
                TODO

            case Large:
                TODO
            }
        }

    @Override
    (Key[] keys, immutable Const? cookie) keysAt(Int txId, immutable Const? cookie = Null)
        {
        if (cookie != Null)
            {
            TODO
            }

        Int size = sizeAt(txId);
        if (size == 0)
            {
            return ([], Null);
            }

        switch (model)
            {
            case Empty:
                assert; // we already checked size 0 above

            case Small:
                // all the keys and values are in memory; just ship all the keys back in one array
                Key[]   keys        = new Key[](size);
                Int     readId      = txId;
                val     histEntries = history.entries.iterator();
                WriteTx: if (Changes tx := checkTx(txId))
                    {
                    readId = tx.readId;
                    if (tx.peekMods().empty)
                        {
                        break WriteTx;
                        }

                    // complicated: keep an iterator of the changes to merge into the iterator of
                    // the underlying (readId) transaction version
                    val modEntries = tx.ensureMods().entries.iterator();
                    assert var modEntry := modEntries.next();

                    // create an iterator of the keys in the history to use as the "main" iterator
                    NextKey: while (true)
                        {
                        if (val histEntry := histEntries.next())
                            {
                            while (true)
                                {
                                // determine if we are at a junction point between the history and
                                // the transactional modifications
                                switch (histEntry.key <=> modEntry.key)
                                    {
                                    case Lesser:
                                        // this is the common case: lots more keys in the history
                                        // than in a given transaction
                                        SkiplistMap<Int, Value|Deletion> byTx = histEntry.value;
                                        if (Int floorId := byTx.floor(readId),
                                                Value|Deletion value := byTx.get(floorId),
                                                !value.is(Deletion))
                                            {
                                            keys += histEntry.key;
                                            }
                                        continue NextKey;

                                    case Equal:
                                        if (!modEntry.value.is(Deletion))
                                            {
                                            keys += modEntry.key; // i.e. same as histEntry.key
                                            }

                                        if (modEntry := modEntries.next())
                                            {
                                            continue NextKey;
                                            }
                                        else
                                            {
                                            // we have exhausted the transaction's modifications;
                                            // just break out and drain the remainder of the keys
                                            // in the map history
                                            break NextKey;
                                            }

                                    case Greater:
                                        // the mod appears to be an insert
                                        if (!modEntry.value.is(Deletion))
                                            {
                                            keys += modEntry.key;
                                            }

                                        if (modEntry := modEntries.next())
                                            {
                                            break; // do NOT go to NextKey
                                            }
                                        else
                                            {
                                            // we have exhausted the transaction's modifications;
                                            // just break out and drain the remainder of the keys
                                            // in the map history
                                            break NextKey;
                                            }
                                    }
                                }
                            }
                        else
                            {
                            // we have exhausted the history, so drain the remainder of the mods
                            for (modEntry : modEntries)
                                {
                                if (!modEntry.value.is(Deletion))
                                    {
                                    keys += modEntry.key;
                                    }
                                }

                            break NextKey;
                            }
                        }
                    }

                // take whatever keys remain in the history iterator
                for (val histEntry : histEntries)
                    {
                    SkiplistMap<Int, Value|Deletion> byTx = histEntry.value;

                    if (Int txFloor := byTx.floor(readId),
                            Value|Deletion value := byTx.get(txFloor),
                            !value.is(Deletion))
                        {
                        keys += histEntry.key;
                        }
                    }

                assert keys.size == size;
                return keys.freeze(inPlace=True), Null;

            case Medium:
                TODO

            case Large:
                TODO
            }
        }

    @Override
    conditional Value load(Int txId, Key key)
        {
        while (Changes tx := checkTx(txId))
            {
            if (Value|Deletion value := tx.peekMods().get(key))
                {
                if (value.is(Deletion))
                    {
                    return False;
                    }
                return True, value;
                }

            txId = tx.readId;
            }

        assert isReadTx(txId);
        switch (model)
            {
            case Empty:
                return False;

            case Small:
                // the entire MapStore is cached in the history map
                // SkiplistMap<Key, SkiplistMap<Int, Value|Deletion>> history
                if (val keyHistory := history.get(key), Int ver := keyHistory.floor(txId))
                    {
                    assert Value|Deletion value := keyHistory.get(ver);
                    if (value.is(Deletion))
                        {
                        return False;
                        }
                    return True, value;
                    }
                return False;

            case Medium:
                TODO

            case Large:
                TODO
            }
        }

    @Override
    void store(Int txId, Key key, Value value)
        {
        storeImpl(txId, key, value);
        }

    @Override
    void delete(Int txId, Key key)
        {
        storeImpl(txId, key, Deletion.Deleted);
        }

    // REVIEW something like this? -> protected Boolean storeImpl(Int txId, Key key, Value|Deletion value, Boolean blind)
    protected void storeImpl(Int txId, Key key, Value|Deletion value)
        {
        assert Changes tx := checkTx(txId, writing=True);
        OrderedMap<Key, Value|Deletion> mods = tx.ensureMods();
        if (Value|Deletion current := mods.get(key))
            {
            if (&value != &current)
                {
                if (value.is(Deletion) && !existsAt(tx.readId, key))
                    {
                    mods.remove(key);
                    }
                else
                    {
                    mods.put(key, value);
                    }
                }
            }
        else if (!(value.is(Deletion) && !existsAt(tx.readId, key)))
            {
            mods.put(key, value);
            }
        }
    }
