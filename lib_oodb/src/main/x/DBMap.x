/**
 * The database interface for a "table" of information, represented as a _dictionary_ style data
 * structure of key/value pairs: the `Map` interface.
 *
 * A `DBMap` is always transactional.
 */
interface DBMap<Key, Value>
        extends Map<Key, Value>
        extends DBObject
    {
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
         * DateTime. If the DBMap is not audited, then this list will contain the current Entry, and
         * if a modification is in the process of occurring, the original Entry.
         */
        @RO List<Entry> changeLog;
        }

    @Override
    @RO Boolean transactional.get()
        {
        return True;
        }

    /**
     * An interface representing a generic trigger on a `DBMap`.
     */
    interface Trigger
        {
        /**
         * `True` iff the trigger is interested in changes made by other triggers. It is possible
         * for cascading triggers to result in infinite loops; in such a case, the database will be
         * killed (if the database engine is well implemented) or lock up (if it is not).
         */
        @RO Boolean cascades;

        /**
         * Determine if this Trigger applies to the specified change. This method must **not** make
         * any further modifications to the database.
         *
         * @param oldEntry  the old value
         * @param newEntry  the new value
         *
         * @return `True` iff the trigger is interested in the specified change
         */
        Boolean appliesTo(Entry oldEntry, Entry newEntry);

        /**
         * `True` iff the trigger fires _after_ the transaction commits.
         */
        @RO Boolean async;

        /**
         * Execute the Trigger functionality. This method may make modifications to the database.
         *
         * @param oldEntry  the old value
         * @param newEntry  the new value
         */
        void process(Entry oldEntry, Entry newEntry);
        }
    }
