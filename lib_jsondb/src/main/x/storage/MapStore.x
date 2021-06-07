import model.DBObjectInfo;

/**
 * Provides a key/value storage service for JSON formatted data on disk.
 *
 * TODO API using URI string instead of (or in addition to) Key type
 * TODO API using String or Reader/Writer instead of (or in addition to) Value type
 * TODO expose Key and Value serializers and deserializers
 * TODO select i.e. query support
 * TODO bulk operations (including deletes, stores, and "in place processing")
 */
service MapStore<Key extends immutable Const, Value extends immutable Const>
        (Catalog catalog, DBObjectInfo info, Appender<String> errs)
        extends ObjectStore(catalog, info, errs)
    {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * An internal [Changes] object for each pending transaction that has modified data.
     */
    protected SkiplistMap<Int, Changes> pending = new SkiplistMap();

    /**
     * An internal, mutable record of Changes for a specific transaction.
     */
    protected static class Changes<Key extends immutable Orderable, Value extends immutable Object>
        {
        /**
         * This txId, the "write" txId.
         */
        Int txWriteId;

        /**
         * The read txId that this transaction is based from.
         */
        Int txReadId;

        /**
         * A map of inserted and updated key/value pairs, keyed by the internal URI form.
         */
        Map<Key, Value> modified = new SkiplistMap();

        /**
         * A set of deleted keys, keyed by the internal URI form.
         */
        Set<String> deleted = new SkiplistSet();
        }

    protected enum Action{Insert, Update, Delete}

    protected static class Change
        (
        String uri,
        Key    key,
        Action action,
        Value? newValue,
        Value? oldValue,
        );

    /**
     * Obtain the set of [Changes] for the specified write transaction, or create a new `Changes` if
     * none has already been recorded.
     *
     * @param txId  the write txId
     *
     * @return the `Changes` for the txId
     */
    Changes changesFor(Int txId)
        {
        TODO
//        return pending.computeIfAbsent(txId, () ->
//            {
//            TODO txManager
//            });
        }

    /**
     * TODO
     */
    conditional Changes hasChanges(Int txId)
        {
        TODO if (pending.contains())
        }


    // ----- ObjectStore life cycle ----------------------------------------------------------------

    // ----- ObjectStore transaction handling ------------------------------------------------------

    @Override
    (Boolean success, Boolean containsMutation) prepare(Int readId, Int writeId, Int prepareId)
        {
        TODO
        }

    @Override
    Boolean mergeTx(Int fromTxId, Int toTxId)
        {
        TODO
        }

    @Override
    (Boolean success) commit(Int prepareId, Int oldestId)
        {
        TODO
        }

    @Override
    void rollback(Int uncommittedId)
        {
        assert isWriteTx(uncommittedId);
        TODO
        }

    @Override
    void retainTx(Set<Int> inUseTxIds, Boolean force = False)
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
            StorageModel actual = quantity.maxOf(weight);

            TODO
            }

        return False;
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
        TODO
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
        TODO
        }

    /**
     * Determine if this map contains the specified key at the completion of the specified
     * transaction id. The key must be specified in its domain model form, or in the JSON URI form,
     * or both if both are available.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request; may be a read or write txId
     * @param key   specifies the key in the Ecstasy domain model form, if available
     * @param uri   specifies the key in the JSON-serialized URI form, if available
     *
     * @return the True iff the specified key exists in the map
     */
    Boolean existsAt(Int txId, Key? key, String? uri = Null)
        {
        TODO
        }

    /**
     * Obtain an iterator over all of the keys (in their internal URI format) that existed at the
     * completion of the specified transaction id.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request; may be a read or write txId
     *
     * @return an Iterator of the keys, in the internal JSON URI format used for key storage, that
     *         were present in the DBMap as of the specified transaction
     */
    Iterator<String> urisAt(Int txId)
        {
        if (isWriteTx(txId))
            {
            Changes tx = changesFor(txId);

            }
//                ? new TxKeyIterator(txId)
//            {
//            }
        TODO
        }

    /**
     * Obtain an iterator over all of the keys that existed at the completion of the specified
     * transaction id.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request; may be a read or write txId
     *
     * @return an Iterator of the Key objects in the DBMap as of the specified transaction
     */
    Iterator<Key> keysAt(Int txId)
        {
        TODO
        }

    /**
     * Obtain the value associated with the specified key, iff that key is present in the map. If
     * the key is not present in the map, then this method returns a conditional `False`.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request; may be a read or write txId
     * @param key   specifies the key in the Ecstasy domain model form, if available
     * @param uri   specifies the key in the JSON-serialized URI form, if available
     *
     * @return the True iff the specified key exists in the map
     *
     * @return a True iff the value associated with the specified key exists in the DBMap as of the
     *         specified transaction
     * @return the value associated with the specified key (conditional)
     */
    conditional Value load(Int txId, Key? key, String? uri = Null)
        {
        TODO
        }

    /**
     * Insert or update a key/value pair into the persistent storage, as part of the specified
     * transaction.
     *
     * @param txId   the transaction identifier that specifies the point in the transactional history
     *               of the storage at which to evaluate the request; must be a write txId
     * @param key    specifies the key in the Ecstasy domain model form, if available
     * @param value  the value to associate with the specified key
     * @param uri    specifies the key in the JSON-serialized URI form, if available
     */
    void store(Int txId, Key? key, Value value, String? uri = Null)
        {
        TODO
        }


    /**
     * Remove the specified key and any associated value from this map.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request; must be a write txId
     * @param key   specifies the key in the Ecstasy domain model form, if available
     * @param uri   specifies the key in the JSON-serialized URI form, if available
     *
     * @return True if the specified key was in the database, and now will be deleted by this
     *         transaction
     */
    Boolean delete(Int txId, Key? key, String? uri = Null)
        {
        TODO
        }
    }
