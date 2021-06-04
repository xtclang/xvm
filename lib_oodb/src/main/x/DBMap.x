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
        return DBValue;
        }


    // ----- Map.Entry extensions ------------------------------------------------------------------

    @Override
    interface Entry
        {
        /**
         * The Date/Time when this Entry was modified (or the commit time for that modification).
         */
        @RO DateTime modified;

        /**
         * The Entry as it existed at the start of the current transaction; if no transaction is
         * active, then this will be the same Entry as `this`.
         */
        @RO Entry original;

        /**
         * If the DBMap is audited, then this contains previous versions of the Entry, indexed by
         * DateTime. If the DBMap is not audited, then this list will contain at least the current
         * Entry, and if a modification is in the process of occurring, the original Entry.
         */
        @RO List<Entry> changeLog;
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

    /**
     * Represents an automatic response to a change that occurs when a transaction commits.
     *
     * This interface can be used in lieu of the more generic [DBObject.Trigger] interface, but it
     * exists only as a convenience, in that it can save the application developer a few type-casts
     * that might otherwise be necessary.
     */
    @Override
    static interface Trigger<TxChange extends DBMap.TxChange>
            extends DBObject.Trigger<TxChange>
        {
        }
    }
