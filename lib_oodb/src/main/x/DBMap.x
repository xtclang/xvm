/**
 * The database interface for a "table" of information, represented as a _dictionary_ style data
 * structure of key/value pairs: the `Map` interface.
 *
 * A `DBMap` is always transactional.
 */
interface DBMap<Key extends immutable Const, Value extends immutable Const>
        extends Map<Key, Value>
        extends DBObject
    {
    // ----- DBObject methods ----------------------------------------------------------------------

    @Override
    @RO DBCategory dbCategory.get()
        {
        return DBMap;
        }

    /**
     * Perform an requirement-test against a specific entry in the DBMap. Despite the naive default
     * implementation provided as part of the DBMap interface, regarding the rechecking of the
     * requirement-test before commit, the API contract for this method allows the database to omit
     * rechecking any changes that do not impact the specified key.
     *
     * @param key   the key of the entry to apply the requirement-test to
     * @param test  the requirement-test function to evaluate against the specified entry
     *
     * @return the `Result` of evaluating the function against the specified entry
     */
    <Result extends immutable Const> Result require(Key key, function Result(Entry) test)
        {
        return require(map -> map.process(key, test));
        }

    /**
     * Perform a blind adjustment of a specific entry in the DBMap. Despite the naive default
     * implementation provided as part of the DBMap interface, regarding the forced evaluation of
     * the deferred adjustment on any access to the DBMap, the API contract for this method allows
     * the database to continue to defer adjustments to the end of the transaction if the otherwise-
     * forcing method calls would not be effected by the specified key.
     *
     * @param key     the key of the entry to apply the blind adjustment to
     * @param adjust  a function that operates against (and may both read and modify) the entry
     */
    void defer(Key key, function Boolean(Entry) adjust)
        {
        defer(map -> map.process(key, adjust));
        }


    // ----- Map.Entry extensions ------------------------------------------------------------------

    @Override
    interface Entry
        {
        /**
         * The Entry as it existed at the start of the current transaction; if no transaction is
         * active, then this will be the same Entry as `this`. No changes are possible through the
         * returned Entry.
         */
        @RO Entry original;
        }


    // ----- transactional information -------------------------------------------------------------

    /**
     * Represents a change to a database `Map`. Changes are represented by the key/value pairs that
     * are added and/or removed from the map:
     *
     * * An "insert" is represented by an entry in the [added] map;
     * * An "update" is represented by an entry in both the [added] and the [removed] map;
     * * A "delete" is represented by an entry in the [removed] map;
     *
     * This interface represents the change without the context of the `DBMap`, thus it is `static`,
     * and cannot provide a before and after view on its own; when combined with the `TxChange`
     * interface, it can provide both the change information, and a before/after view of the data.
     */
    static interface DBChange<Key, Value>
        {
        /**
         * The key/value pairs inserted-into/updated-in the `DBMap`.
         *
         * The returned `Map` does not allow mutation, but if the transaction is still processing,
         * any items subsequently added or updated within the transaction _may_ appear in the `Map`.
         *
         * To construct the [post] map from the [pre] map, make a copy of the [pre] map, then remove
         * the key/value pairs specified by [removed] from that map, then add the key/value pairs
         * specified by [added] into that map.
         */
        @RO Map<Key, Value> added;

        /**
         * The key/value pairs updated-in/deleted-from the `Map`.
         *
         * The returned `Map` does not allow mutation, but if the transaction is still processing,
         * any items subsequently updated or removed within the transaction _may_ appear in the
         * `Map`.
         *
         * To construct the [post] map from the [pre] map, make a copy of the [pre] map, then remove
         * the key/value pairs specified by [removed] from that map, then add the key/value pairs
         * specified by [added] into that map.
         */
        @RO Map<Key, Value> removed;
        }

    /**
     * Represents a transactional change to a database `Map`.
     *
     * This interface provides both the discrete change information, as well as the contextual
     * before-and-after view of the transaction. Obtaining a 'before' or 'after' transactional
     * view of the map should be assumed to be a relatively expensive operation, particularly
     * for an historical `TxChange` (one pulled from some previous point in the a commit history).
     */
    @Override
    interface TxChange
            extends DBChange<Key, Value>
        {
        }


    // ----- transaction trigger API ---------------------------------------------------------------

    // these interfaces can be used in lieu of the more generic interfaces of the same names found
    // on [DBObject], but these exists only as a convenience, in that they can save the application
    // database developer a few type-casts that might otherwise be necessary.

    @Override static interface Validator<TxChange extends DBMap.TxChange>
            extends DBObject.Validator<TxChange> {}
    @Override static interface Rectifier<TxChange extends DBMap.TxChange>
            extends DBObject.Rectifier<TxChange> {}
    @Override static interface Distributor<TxChange extends DBMap.TxChange>
            extends DBObject.Distributor<TxChange> {}
    }
