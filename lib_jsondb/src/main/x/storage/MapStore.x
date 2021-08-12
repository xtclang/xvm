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
service MapStore<Key extends immutable Const, Value extends immutable Const>
        extends ObjectStore
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


    // ----- ObjectStore life cycle ----------------------------------------------------------------

    // ----- ObjectStore transaction handling ------------------------------------------------------

    @Override
    PrepareResult prepare(Int writeId, Int prepareId)
        {
        TODO
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

    /**
     * Determine if the Map is empty at the completion of the specified transaction id.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request; may be a read or write txId
     *
     * @return True iff no key/value pairs exist in the DBMap as of the specified transaction
     */
    Boolean emptyAt(Int txId)
        {
        // no obvious way to optimize this call without calculating the size, since size information
        // gets cached in memory
        return sizeAt(txId) == 0;
        }

    /**
     * Determine the size of the Map at the completion of the specified transaction id.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request; may be a read or write txId
     *
     * @return the number of key/value pairs in the DBMap as of the specified transaction
     */
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

    /**
     * Determine if this map contains the specified key at the completion of the specified
     * transaction id. The key must be specified in its domain model form, or in the JSON URI form,
     * or both if both are available.
     *
     * @param txId  the "write" transaction identifier
     * @param key   specifies the key to test for
     *
     * @return the True iff the specified key exists in the map
     */
    Boolean existsAt(Int txId, Key key)
        {
        while (Changes tx := checkTx(txId))
            {
            if (Value|Deletion value := tx.peekMods().get(key))
                {
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
                    // return &value != &Deleted; // TODO GG
                    return !value.is(Deletion);
                    }
                return False;

            case Medium:
                TODO

            case Large:
                TODO
            }
        }

    /**
     * Obtain an iterator over all of the keys that exist for the specified transaction.
     *
     * @param txId  the transaction identifier
     *
     * @return an Iterator of the Key objects in the DBMap as of the specified transaction
     */
    Iterator<Key> keysAt(Int txId)
        {
        TODO
        }

    /**
     * Obtain the value associated with the specified key (in its internal URI format), iff that key
     * is present in the map. If the key is not present in the map, then this method returns a
     * conditional `False`.
     *
     * @param txId  the "write" transaction identifier
     * @param key   specifies the key in the Ecstasy domain model form, if available
     *
     * @return a True iff the value associated with the specified key exists in the DBMap as of the
     *         specified transaction
     * @return (conditional) the value associated with the specified key
     */
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

    /**
     * Insert or update a key/value pair into the persistent storage, as part of the specified
     * transaction.
     *
     * @param txId   the "write" transaction identifier
     * @param key    specifies the key
     * @param value  the value to associate with the specified key
     */
    void store(Int txId, Key key, Value value)
        {
        storeImpl(txId, key, value);
        }

    /**
     * Remove the specified key and any associated value from this map.
     *
     * @param txId  the "write" transaction identifier
     * @param key   specifies the key
     */
    void delete(Int txId, Key key)
        {
        storeImpl(txId, key, Deletion.Deleted);
        }

    // TODO something like this? -> protected Boolean storeImpl(Int txId, Key key, Value|Deletion value, Boolean blind)
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

// TODO
//    /**
//     * Obtain an iterator over all of the keys and values that exist for the specified transaction.
//     *
//     * @param txId  the transaction identifier
//     *
//     * @return an Iterator of the Key and Value objects in the DBMap as of the specified transaction
//     */
//    Iterator<Tuple<Key,Value>> keysAndValuesAt(Int txId)
//        {
//        TODO
//        }
//
//    /**
//     * Obtain an iterator over all of the keys (in their internal URI format) for the specified
//     * transaction.
//     *
//     * @param txId  the transaction identifier
//     *
//     * @return an Iterator of the keys, in the internal JSON URI format used for key storage, that
//     *         were present in the DBMap as of the specified transaction
//     */
//    Iterator<String> urisAt(Int txId)
//        {
//        TODO
//        }
//
//    /**
//     * Obtain an iterator over all of the keys (in their internal URI format) and values that exist
//     * for the specified transaction.
//     *
//     * @param txId  the transaction identifier
//     *
//     * @return an Iterator of the keys and values, with the keys in the internal JSON URI format
//     *         used for key storage
//     */
//    Iterator<Tuple<String,Value>> urisAndValuesAt(Int txId)
//        {
//        TODO
//        }
//
//    /**
//     * Obtain the value associated with the specified key, iff that key is present in the map. If
//     * the key is not present in the map, then this method returns a conditional `False`.
//     *
//     * @param txId  the "write" transaction identifier
//     * @param key   specifies the key in the Ecstasy domain model form, if available
//     *
//     * @return a True iff the value associated with the specified key exists in the DBMap as of the
//     *         specified transaction
//     * @return (conditional) the value associated with the specified key
//     */
//    conditional Value loadByUri(Int txId, String uri)
//        {
//        TODO
//        }
    }
