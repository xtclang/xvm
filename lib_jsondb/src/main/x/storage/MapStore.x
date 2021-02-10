import model.DBObjectInfo;

/**
 * Provides a key/value storage service for JSON formatted data on disk.
 *
 * TODO API using URI string instead of (or in addition to) Key type
 * TODO API using String or Reader/Writer instead of (or in addition to) Value type
 * TODO expose Key and Value serializers and deserializers
 */
service MapStore<Key, Value>(Catalog catalog, DBObjectInfo info, Appender<String> errs)
        extends ObjectStore(catalog, info, errs)
    {
    /**
     * Determine if the Map is empty at the completion of the specified transaction id.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request
     */
    Boolean emptyAt(Int txId)
        {
        TODO
        }

    /**
     * Determine the size of the Map at the completion of the specified transaction id.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request
     */
    Int sizeAt(Int txId)
        {
        TODO
        }

    /**
     * Determine if this map contains the specified key at the completion of the specified
     * transaction id.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request
     * @param key   specifies the key that may or may not already be present in the map
     *
     * @return the True iff the specified key exists in the map
     */
    Boolean existsAt(Int txId, Key key)
        {
        TODO
        }

    /**
     * Obtain an iterator over all of the keys that existed at the completion of the specified
     * transaction id.
     */
    Iterator<Key> keysAt(Int txId)
        {
        TODO
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
    conditional Value load(Int txId, Key key)
        {
        TODO
        }

    // TODO select i.e. query support

    /**
     * Insert, update, and delete the specified data, as part of the specified transaction.
     *
     * @param txId      the transaction identifier
     * @param modified  the keys and values to store
     * @param deleted   the keys to delete
     */
    void commit(Int txId, Map<Key, Value> modified, Key[] deleted)
        {
        for ((Key key, Value value) : modified)
            {
            store(txId, key, value);
            }

        for (Key key : deleted)
            {
            delete(txId, key);
            }
        }

    /**
     * Insert or update a key/value pair into the persistent storage, as part of the specified
     * transaction.
     *
     * @param txId   the transaction identifier
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key
     */
    void store(Int txId, Key key, Value value)
        {
        TODO
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
    void delete(Int txId, Key key)
        {
        TODO
        }
    }
