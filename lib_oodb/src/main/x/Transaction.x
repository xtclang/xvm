/**
 * A database transaction.
 */
interface Transaction
        extends DBSchema
        extends Closeable
    {
    enum Status {Active, Committing, Committed, RolledBack}

    /**
     * The transaction status.
     */
    @RO Status status;

    /**
     * When the transaction began.
     */
    @RO DateTime created;

    /**
     * When the transaction closed with a commit or roll-back.
     */
    @RO DateTime? retired;

    /**
     * The time consumed by the transaction, from creation to the beginning of the commit.
     */
    @RO Duration transactionTime;

    /**
     * The timeout specified for the transaction, if it has not been retired. (The value of this
     * property is undefined for retired transactions.)
     */
    @RO Duration? timeout;

    /**
     * The time consumed by the commit processing for the transaction.
     */
    @RO Duration commitTime;

    /**
     * Allows the transaction to be marked as not-commit-able. This property can be set to `True`,
     * but cannot be set to `False` once it has been set to `True`.
     */
    Boolean rollbackOnly;

    /**
     * Commit the transaction.
     *
     * @return `True` iff the commit succeeded
     */
    Boolean commit();

    /**
     * Roll back the transaction.
     */
    void rollback();

    @Override
    void close()
        {
        if (status == Active)
            {
            if (rollbackOnly)
                {
                rollback();
                }
            else
                {
                commit();
                }
            }
        }

    /**
     * The optional identifier provided when the transaction was created.
     */
    @RO UInt? id;

    /**
     * An optional descriptive name provided when the transaction was created.
     */
    @RO String? name;

    /**
     * Transaction priority, potentially used to determine resource allocations for active
     * transactions, and to order commits among a backlog of transactions.
     *
     * * `Idle` - Bottom-most priority, only guaranteed to execute if nothing else is executing.
     *
     * * `Low` - Lower than normal priority.
     *
     * * `Normal` - The default priority.
     *
     * * `High` - Higher than normal priority.
     *
     * * `System` - Highest priority; the `System` priority cannot be assigned to a transaction, but
     *   is instead automatically associated with transactions initiated by the database system
     *   itself
     */
    enum Priority {Idle, Low, Normal, High, System}

    /**
     * The priority of the transaction.
     */
    @RO Priority priority;

    /**
     * The number of times that the execution of the work represented by this transaction was
     * attempted previously without success, as indicated by the creator of this transaction.
     */
    @RO Int retryCount;

    /**
     * If this transaction was created as a side-effect of another transaction, then this property
     * provides that transaction.
     */
    @RO Transaction? origin;

    /**
     * The user that initiated this transaction, or `Null` if the transaction was initiated by the
     * database.
     */
    @RO Connection.User? user;

    /**
     * A list of transaction _conditions_ that indicate additional requirements that must be met for
     * the transaction to be able to commit. The conditions are not expected to be retained in the
     * historical transaction record.
     */
    @RO List<function Boolean()> conditions;

    /**
     * The contents of the transaction, which define the total net change represented by the
     * transaction.
     */
    @RO Map<String, Change> contents;

    typedef (ListChange | MapChange | QueueChange | LogChange | CounterChange) Change;

    /**
     * Represents a change to a database list. Most list changes are extremely compact, representing
     * a limited set of additions and/or removals from the previous version of the list; however, a
     * list may be used to represent the order of a set of values, and if that order changes, the
     * change may be such that it is easiest to represent as the removal of all previous contents,
     * followed by the addition of those same contents in a different order.
     *
     * If the transaction is historical, the "pre" and "post" versions of the `List` may not be
     * available, and if they are available, they may be expensive to reconstruct.
     */
    static interface ListChange<Element>
        {
        /**
         * The name of the `List` whose contents were changed.
         */
        @RO String name;

        /**
         * The contents of the `List`, before this change was made.
         *
         * The returned `List` does not allow mutation.
         */
        @RO List<Element> pre;

        /**
         * The contents of the `List`, after this change was made.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any subsequent changes within the transaction _may_ appear in the list.
         */
        @RO List<Element> post;

        /**
         * The elements added to the `List`. The key of this map is the index of the added element
         * in the [post] list, and the value of this map is the added element value.
         *
         * The returned `Map` does not allow mutation, but if the transaction is still processing,
         * any items subsequently added within the transaction _may_ appear in the list.
         *
         * To construct the [post] list from the [pre] list, make a copy of the [pre] list, then
         * remove the elements specified by [removed] from that list, then add the elements
         * specified by [added] into that list.
         */
        @RO Map<Int, Element> added;

        /**
         * The elements removed from the `List`. The key of this map is the index into the [pre]
         * list of each element to remove, and the value of this map is the element value at that
         * index.
         *
         * The returned `Map` does not allow mutation, but if the transaction is still processing,
         * any items subsequently removed within the transaction _may_ appear in the list.
         *
         * To construct the [post] list from the [pre] list, make a copy of the [pre] list, then
         * remove the elements specified by [removed] from that list, then add the elements
         * specified by [added] into that list.
         */
        @RO Map<Int, Element> removed;
        }

    /**
     * Represents a change to a database `Map`. Changes are represented by the key/value pairs that
     * are added and/or removed from the map:
     *
     * * An "insert" is represented by an entry in the [added] map;
     * * An "update" is represented by an entry in both the [added] and the [removed] map;
     * * A "delete" is represented by an entry in the [removed] map;
     *
     * If the transaction is historical, the "pre" and "post" versions of the `Map` may not be
     * available, and if they are available, they may be expensive to reconstruct.
     */
    static interface MapChange<Key, Value>
        {
        /**
         * The name of the `Map` whose contents were changed.
         */
        @RO String name;

        /**
         * The contents of the `Map`, before this change was made. Generating this `pre` view of the
         * `Map` may be quite expensive.
         *
         * The returned `Map` does not allow mutation.
         */
        Map<Key, Value> pre;

        /**
         * The contents of the `Map`, after this change was made.  Generating this `post` view of
         * the `Map` may be quite expensive.
         *
         * The returned `Map` does not allow mutation, but if the transaction is still processing,
         * any items subsequently changed within the transaction _may_ appear in the `Map`.
         */
        Map<Key, Value> post;

        /**
         * The key/value pairs inserted-into/updated-in the `Map`.
         *
         * The returned `Map` does not allow mutation, but if the transaction is still processing,
         * any items subsequently added or updated within the transaction _may_ appear in the `Map`.
         *
         * To construct the [post] map from the [pre] map, make a copy of the [pre] map, then remove
         * the key/value pairs specified by [removed] from that map, then add the key/value pairs
         * specified by [added] into that map.
         */
        Map<Key, Value> added;

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
        Map<Key, Value> removed;
        }

    /**
     * Represents a change to a database queue.
     *
     * The `QueueChange` does not provide a "pre" and "post" view of the Queue, because Queue
     * contents are opaque.
     */
    static interface QueueChange<Element>
        {
        /**
         * The name of the `Queue` whose contents were changed.
         */
        @RO String name;

        /**
         * The elements appended to the `Queue`.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently added within the transaction _may_ appear in the list.
         */
        List<Element> added;

        /**
         * The elements taken from the `Queue`.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently taken within the transaction _may_ appear in the list.
         */
        List<Element> removed;
        }

    /**
     * Represents additions to a database log.
     *
     * The `LogChange` does not provide a "pre" and "post" view of the Log, because Log contents
     * are extra-transactional.
     *
     * In the historical transaction record, this information is likely to be discarded, and thus
     * unavailable.
     */
    static interface LogChange<Element>
        {
        /**
         * The name of the `Log` that has had contents added.
         */
        @RO String name;

        /**
         * The elements appended to the `Log`.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently logged within the transaction _may_ appear in the list.
         *
         * In the historical transaction record, this information is likely to be discarded, and
         * thus is likely to be unavailable.
         */
        List<Element> added;
        }

    /**
     * Represents values emitted by a database counter.
     *
     * The `CounterChange` does not provide a "pre" and "post" view of the Counter, because the
     * Counter state is extra-transactional.
     *
     * In the historical transaction record, this information is likely to be discarded, and thus
     * unavailable.
     */
    static interface CounterChange
        {
        /**
         * The name of the `Counter` that has generated values within this transaction.
         */
        @RO String name;

        /**
         * The list of the values emitted by the counter within and on behalf of this transaction,
         * which in effect "removes them" from the potential values that can be subsequently emitted
         * by the counter.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently emitted by the counter within the transaction _may_ appear in the
         * list.
         *
         * In the historical transaction record, this information is likely to be discarded, and
         * thus is likely to be unavailable.
         */
        List<UInt> removed;
        }
    }
