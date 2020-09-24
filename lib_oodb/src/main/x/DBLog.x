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
         * The `DateTime` that the item was logged.
         */
        @RO DateTime datetime;

        /**
         * The [DBTransaction] within which the item was logged. Note that it may be expensive to
         * obtain historical transactional data.
         */
        @RO DBTransaction transaction;

        /**
         * The element that was logged.
         */
        @RO Element value;

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

    /**
     * Represents additions to a transactional database log.
     */
    @Override
    interface Change
        {
        /**
         * The elements appended to the `Log`.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently logged within the transaction _may_ appear in the list.
         */
        List<Element> added;

        /**
         * The elements removed from the `Log`, if any log truncation occurred.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently logged within the transaction _may_ appear in the list.
         */
        List<Element> removed;
        }
    }
