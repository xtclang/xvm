/**
 * A key/value storage API.
 */
interface MapStore<Key extends immutable Const, Value extends immutable Const> {
    // ----- Map operations support ----------------------------------------------------------------

    /**
     * Determine if the Map is empty at the completion of the specified transaction id.
     *
     * @param txId  the transaction identifier that specifies the point in the transactional history
     *              of the storage at which to evaluate the request; may be a read or write txId
     *
     * @return True iff no key/value pairs exist in the DBMap as of the specified transaction
     */
    Boolean emptyAt(Int txId) {
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
    Int sizeAt(Int txId);

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
    Boolean existsAt(Int txId, Key key) {
        return load(txId, key);
    }

    /**
     * Obtain an iterator over all of the keys that exist for the specified transaction.
     *
     * @param txId    the transaction identifier
     * @param cookie  the previously returned cookie, or Null
     *
     * @return keys    an array of zero or more keys from the store
     * @return cookie  a non-null value indicates that there may be more keys available from the
     *                 store; to obtain those keys, the client must make a later call to this
     *                 method, passing this cookie
     */
    (Key[] keys, immutable Const? cookie) keysAt(Int txId, immutable Const? cookie = Null);

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
    conditional Value load(Int txId, Key key);

    /**
     * Insert or update a key/value pair into the persistent storage, as part of the specified
     * transaction.
     *
     * @param txId   the "write" transaction identifier
     * @param key    specifies the key
     * @param value  the value to associate with the specified key
     */
    void store(Int txId, Key key, Value value);

    /**
     * Remove the specified key and any associated value from this map.
     *
     * @param txId  the "write" transaction identifier
     * @param key   specifies the key
     */
    void delete(Int txId, Key key);

// TODO
//    /**
//     * Obtain an iterator over all of the keys and values that exist for the specified transaction.
//     *
//     * @param txId  the transaction identifier
//     *
//     * @return an Iterator of the Key and Value objects in the DBMap as of the specified transaction
//     */
//    Iterator<Tuple<Key,Value>> keysAndValuesAt(Int txId);
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
//    Iterator<String> urisAt(Int txId);
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
//    Iterator<Tuple<String,Value>> urisAndValuesAt(Int txId);
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
//    conditional Value loadByUri(Int txId, String uri);
}
