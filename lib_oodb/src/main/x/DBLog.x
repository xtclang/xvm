/**
 * The database interface for a "log" of information. A log is an opaque, append-only entity, until
 * it is viewed as a list, which may be very expensive.
 *
 * A `DBLog` may be extra-transactional, which means that elements appended to a `DBLog` may persist
 * even if the transaction that logged those elements does not commit.
 */
interface DBLog<Element>
        extends Appender<Element>
        extends DBObject
    {
    /**
     * Determine if the `DBLog` automatically truncates entries after a period of time.
     *
     * @return `True` iff the `DBLog` is configured to truncate entries older than some age
     * @return (conditional) the `Duration` to retain a log entry before expiring it and allowing it
     *         to be automatically truncated
     */
    conditional Duration hasExpiry();

    /**
     * Determine if the `DBLog` automatically truncates entries after the log exceeds a certain
     * size is reached.
     *
     * @return `True` iff the `DBLog` is configured to truncate entries when the log reaches a
     *         certain size
     * @return (conditional) the maximum size in bytes to use for the `DBLog`
     * @return (conditional) the size in bytes to truncate the `DBLog` down to when the maximum is
     *         reached
     */
    conditional (UInt max, UInt min) hasLimit();

    /**
     * A representation of information that was previously appended to the `DBLog`.
     */
    interface Entry
        {
        /**
         * The element that was logged.
         */
        @RO Element value;

        /**
         * The `DateTime` that the item was logged.
         */
        @RO DateTime datetime;

        /**
         * An estimated size, in bytes, for the log entry.
         */
        @RO UInt estimatedSize;
        }

    /**
     * The contents of the `DBLog`, represented as a `List` of log entries.
     */
    @RO List<Entry> contents;

    /**
     * Truncate all contents of the `DBLog`.
     */
    void truncateAll();

    /**
     * Truncate all contents of the `DBLog` that are older than the specified cut-off.
     *
     * @param cutoff  the `DateTime` of the oldest `Entry` to retain, or the `Duration` indicating
     *                the maximum age of an `Entry` to retain
     */
    void truncateBefore(DateTime|Duration cutoff);

    /**
     * Truncate all contents of the `DBLog` except for the last portion up to the specified size.
     *
     * @param retainSize  the maximum number of bytes to retain
     */
    void truncateExcept(UInt retainSize);


    // ----- DBObject methods ----------------------------------------------------------------------

    @Override
    @RO DBCategory dbCategory.get()
        {
        return DBLog;
        }


    // ----- transactional information -------------------------------------------------------------

    /**
     * Represents specific database changes that occurred to a transactional database log.
     *
     * This interface represents the change without the context of the `DBLog`, thus it is `static`,
     * and cannot provide a before and after view on its own; when combined with the `TxChange`
     * interface, it can provide both the change information, and a before/after view of the data.
     */
    static interface DBChange<Element>
        {
        /**
         * The elements appended to the `Log`.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently logged within the transaction _may_ appear in the list.
         */
        // TODO GG List<DBLog<Element>.Entry> added;
        List<Entry> added;

        /**
         * The elements removed from the `Log`, if any log truncation occurred.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently logged within the transaction _may_ appear in the list.
         */
        // TODO GG List<DBLog<Element>.Entry> removed;
        List<Entry> removed;
        }

    /**
     * Represents a transactional change to a database log.
     *
     * This interface provides both the discrete change information, as well as the contextual
     * before-and-after view of the transaction. Obtaining a 'before' or 'after' transactional view
     * of the log should be assumed to be a relatively expensive operation, particularly for an
     * historical `TxChange` (one pulled from some previous point in the a commit history).
     */
    @Override
    interface TxChange
            extends DBChange<Element>
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
    static interface Trigger<TxChange extends DBLog.TxChange>
            extends DBObject.Trigger<TxChange>
        {
        }
    }
